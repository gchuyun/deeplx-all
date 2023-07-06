package com.gchuyun.deeplx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@RestController("/")
public class DeepLXController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeepLXController.class);

    private final Queue<AsynTranslateJob> asycTranslateJobList = new ConcurrentLinkedQueue<>();

    @Autowired
    DeepLClient deepLClient;

    @Autowired
    ObjectMapper objectMapper;

    @GetMapping(value = "", produces = MediaType.TEXT_PLAIN_VALUE)
    public String index() {
        return "DeepL Free API, Made by sjlleo and missuo. Go to /translate with POST. http://github.com/OwO-Network/DeepLX";
    }

    @PostMapping(value = "translate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<DeepLXDTO.ResponseData>> handleTranslate(String asynId, @RequestBody DeepLXDTO.RequestData reqData) {
        Mono<ResponseEntity<DeepLXDTO.ResponseData>> responseMono;
        List<String> transTexts = Optional.ofNullable(reqData)
                .map(r -> {
                    if (r instanceof DeepLXDTO.RequestData.Single) {
                        return List.of(((DeepLXDTO.RequestData.Single) r).getTransText());
                    }
                    if (r instanceof DeepLXDTO.RequestData.Batch) {
                        return ((DeepLXDTO.RequestData.Batch) r).getTransTexts();
                    }
                    return null;
                })
                .orElse(null);
        if (transTexts == null) {
            responseMono = Mono.just(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                            new DeepLXDTO.ResponseData.Error() {{
                                setMessage("TEXT IS NULL");
                                setCode(HttpStatus.BAD_REQUEST.value());
                            }}));
            return responseMono;
        }
        boolean isSingle = reqData instanceof DeepLXDTO.RequestData.Single;
        if (asynId == null) {
            responseMono = deepLClient.translate(reqData.getSourceLang(), reqData.getTargetLang(), transTexts)
                    .map(deepLRespData -> {
                        DeepLXDTO.ResponseData responseData = extractDeepLXResponseData(deepLRespData, 0, transTexts.size(), isSingle);
                        return ResponseEntity.status(HttpStatus.valueOf(responseData.getCode()))
                                        .body(responseData);
                    });
        } else {
            CompletableFuture<DeepLXDTO.ResponseData> dataFuture = new CompletableFuture<>();
            AsynTranslateJob asynTranslateJob = new AsynTranslateJob() {{
                setAsynId(asynId);
                setRequestData(reqData);
                setResponseFuture(dataFuture);
            }};
            asycTranslateJobList.add(asynTranslateJob);
            responseMono = Mono.fromFuture(dataFuture)
                    .map(responseData ->
                            ResponseEntity.status(HttpStatus.valueOf(responseData.getCode()))
                                    .body(responseData));
        }

        return responseMono;
    }

    @Scheduled(fixedDelay = 1000)
    public void handlePeriodicTranslateAsync() {
        List<AsynTranslateJob> oneBatchJobList = new ArrayList<>();
        asycTranslateJobList.removeIf(item -> {
            if (oneBatchJobList.isEmpty()) {
                oneBatchJobList.add(item);
                return true;
            } else {
                AsynTranslateJob firstJob = oneBatchJobList.get(0);
                if (item.getAsynId().equals(firstJob.getAsynId())
                        && item.getRequestData().getSourceLang().equals(firstJob.getRequestData().getSourceLang())
                        && item.getRequestData().getTargetLang().equals(firstJob.getRequestData().getTargetLang())) {
                    oneBatchJobList.add(item);
                    return true;
                }
            }
            return false;
        });
        if (!oneBatchJobList.isEmpty()) {
            String sourceLang = oneBatchJobList.get(0).getRequestData().getSourceLang();
            String targetLang = oneBatchJobList.get(0).getRequestData().getTargetLang();
            List<String> transTexts = oneBatchJobList.stream()
                    .map(job -> {
                        if (job.getRequestData() instanceof DeepLXDTO.RequestData.Single) {
                            return List.of(((DeepLXDTO.RequestData.Single) job.getRequestData()).getTransText());
                        }
                        return ((DeepLXDTO.RequestData.Batch) job.getRequestData()).getTransTexts();
                    })
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            deepLClient.translate(sourceLang, targetLang, transTexts)
                    .doOnError(throwable -> {
                        oneBatchJobList.forEach(job ->
                                job.getResponseFuture()
                                        .supplyAsync(() -> {
                                            throw new RuntimeException(throwable);
                                        }));
                    })
                    .subscribe(deepLRespData -> {
                        final int[] beginIndex = {0};
                        oneBatchJobList.stream().forEach(job -> {
                            boolean isSingle = job.getRequestData() instanceof DeepLXDTO.RequestData.Single;
                            int size = isSingle ? 1 : ((DeepLXDTO.RequestData.Batch) job.getRequestData()).getTransTexts().size();
                            int endIndex = beginIndex[0] + size;
                            DeepLXDTO.ResponseData responseData = extractDeepLXResponseData(deepLRespData, beginIndex[0], endIndex, isSingle);
                            job.getResponseFuture().complete(responseData);
                            beginIndex[0] = endIndex;
                        });
                    });
        }
    }

    private DeepLXDTO.ResponseData extractDeepLXResponseData(DeepLDTO.ResponseData deepLRespData, int beginIndex, int endIndex, boolean isSingle) {
        if (deepLRespData instanceof DeepLDTO.ResponseData.ErrorData) {
            DeepLDTO.ResponseData.ErrorData errorData = (DeepLDTO.ResponseData.ErrorData) deepLRespData;
            String reson = Optional.ofNullable(errorData)
                    .map(DeepLDTO.ResponseData.ErrorData::getError)
                    .map(DeepLDTO.ResponseData.ErrorData.Error::getData)
                    .map(DeepLDTO.ResponseData.ErrorData.Reason::getWhat)
                    .orElseGet(() ->
                            Optional.ofNullable(errorData)
                                    .map(DeepLDTO.ResponseData.ErrorData::getError)
                                    .map(DeepLDTO.ResponseData.ErrorData.Error::getMessage)
                                    .orElse(HttpStatus.valueOf(errorData.getHttpStatusCode()).getReasonPhrase())
                    );
            return new DeepLXDTO.ResponseData.Error() {{
                setCode(errorData.getHttpStatusCode());
                setMessage(reson);
            }};
        }

        DeepLDTO.ResponseData.ResultData respData = (DeepLDTO.ResponseData.ResultData) deepLRespData;
        if (isSingle) {
            return new DeepLXDTO.ResponseData.Single() {{
                setCode(HttpStatus.OK.value());
                setId(respData.getId());
                setData(respData.getResult().getTexts().get(beginIndex).getText());
                setAlternatives(respData.getResult().getTexts().get(beginIndex).getAlternatives().stream()
                        .map(alt -> alt.getText())
                        .collect(Collectors.toList()));
            }};
        } else {
            return new DeepLXDTO.ResponseData.Batch() {{
                setCode(HttpStatus.OK.value());
                setId(respData.getId());
                setDatas(respData.getResult().getTexts().subList(beginIndex, endIndex).stream().map(t ->
                                new TranlateText() {{
                                    setData(t.getText());
                                    setAlternatives(t.getAlternatives().stream()
                                            .map(alt -> alt.getText())
                                            .collect(Collectors.toList()));
                                }})
                        .collect(Collectors.toList()));
            }};
        }
    }


    @ControllerAdvice
    public static class DeepLXExceptionHandler {

        @ExceptionHandler(value = Exception.class)
        public ResponseEntity<DeepLXDTO.ResponseData> handle(Exception e) {
            LOGGER.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new DeepLXDTO.ResponseData.Error() {{
                        setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
                        setMessage(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
                    }});
        }
    }
}