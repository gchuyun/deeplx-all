package com.gchuyun.deeplx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Controller("/")
public class DeepLXController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeepLXController.class);

    private final Queue<AsynTranslateJob> asycTranslateJobList = new ConcurrentLinkedQueue<>();

    @Inject
    @ReflectiveAccess
    DeepLClient deepLClient;

    @Inject
    @ReflectiveAccess
    ObjectMapper objectMapper;

    @Get()
    @Produces(MediaType.TEXT_PLAIN)
    public String index() {
        return "DeepL Free API, Made by sjlleo and missuo. Go to /translate with POST. http://github.com/OwO-Network/DeepLX";
    }

    @Post("translate")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletableFuture<HttpResponse<DeepLXDTO.ResponseData>> handleTranslate(@Nullable @QueryValue("asynId") String asynId, @Body DeepLXDTO.RequestData reqData) {
        CompletableFuture<HttpResponse<DeepLXDTO.ResponseData>> responseFuture = new CompletableFuture<>();
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
            responseFuture.complete(
                    HttpResponse.status(HttpStatus.BAD_REQUEST).body(
                            new DeepLXDTO.ResponseData.Error() {{
                                setMessage("TEXT IS NULL");
                                setCode(HttpStatus.BAD_REQUEST.getCode());
                            }}));
            return responseFuture;
        }
        boolean isSingle = reqData instanceof DeepLXDTO.RequestData.Single;
        if (asynId == null) {
            deepLClient.translate(reqData.getSourceLang(), reqData.getTargetLang(), transTexts, o -> {
                        try {
                            return objectMapper.writeValueAsString(o);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .whenComplete((deepLRespData, throwable) -> {
                        if (throwable != null) {
                            throw new RuntimeException(throwable);
                        }
                        DeepLXDTO.ResponseData responseData = extractDeepLXResponseData(deepLRespData, 0, transTexts.size(), isSingle);
                        responseFuture.complete(
                                HttpResponse.status(HttpStatus.valueOf(responseData.getCode()))
                                        .body(responseData));
                    });
        } else {
            CompletableFuture<DeepLXDTO.ResponseData> dataFuture = new CompletableFuture<>();
            dataFuture.whenComplete((responseData, throwable) -> {
                if (throwable != null) {
                    throw new RuntimeException(throwable);
                }
                responseFuture.complete(
                        HttpResponse.status(HttpStatus.valueOf(responseData.getCode()))
                                .body(responseData));
            });
            AsynTranslateJob asynTranslateJob = new AsynTranslateJob() {{
                setAsynId(asynId);
                setRequestData(reqData);
                setResponseFuture(dataFuture);
            }};
            asycTranslateJobList.add(asynTranslateJob);
        }

        return responseFuture;
    }

    @Scheduled(fixedDelay = "${deeplx.asyn-period}")
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

            deepLClient.translate(sourceLang, targetLang, transTexts, o -> {
                        try {
                            return objectMapper.writeValueAsString(o);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .whenComplete((deepLRespData, throwable) -> {
                        if (throwable != null) {
                            oneBatchJobList.forEach(job ->
                                    job.getResponseFuture()
                                            .supplyAsync(() -> {
                                                throw new RuntimeException(throwable);
                                            }));
                            return;
                        }
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
                                    .orElse(HttpStatus.valueOf(errorData.getHttpStatusCode()).getReason())
                    );
            return new DeepLXDTO.ResponseData.Error() {{
                setCode(errorData.getHttpStatusCode());
                setMessage(reson);
            }};
        }

        DeepLDTO.ResponseData.ResultData respData = (DeepLDTO.ResponseData.ResultData) deepLRespData;
        if (isSingle) {
            return new DeepLXDTO.ResponseData.Single() {{
                setCode(HttpStatus.OK.getCode());
                setId(respData.getId());
                setData(respData.getResult().getTexts().get(beginIndex).getText());
                setAlternatives(respData.getResult().getTexts().get(beginIndex).getAlternatives().stream()
                        .map(alt -> alt.getText())
                        .collect(Collectors.toList()));
            }};
        } else {
            return new DeepLXDTO.ResponseData.Batch() {{
                setCode(HttpStatus.OK.getCode());
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

    @ReflectiveAccess
    @Produces
    @Singleton
    @Requires(classes = {Exception.class, ExceptionHandler.class})
    public static class DeepLXExceptionHandler implements ExceptionHandler<Exception, HttpResponse> {

        private final ErrorResponseProcessor<?> errorResponseProcessor;

        public DeepLXExceptionHandler(ErrorResponseProcessor<?> errorResponseProcessor) {
            this.errorResponseProcessor = errorResponseProcessor;
        }

        @Override
        public HttpResponse<DeepLXDTO.ResponseData> handle(HttpRequest request, Exception e) {
            LOGGER.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new DeepLXDTO.ResponseData.Error() {{
                        setCode(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                        setMessage(HttpStatus.INTERNAL_SERVER_ERROR.getReason());
                    }});
        }
    }
}