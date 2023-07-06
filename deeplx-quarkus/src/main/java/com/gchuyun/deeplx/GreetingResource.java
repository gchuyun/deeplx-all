package com.gchuyun.deeplx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import io.vertx.core.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Path("/deeplx/")
public class GreetingResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(GreetingResource.class);
    private final Queue<AsynTranslateJob> asycTranslateJobList = new ConcurrentLinkedQueue<>();

    @Inject
    private DeepLClient deepLClient;

    @Inject
    private ObjectMapper objectMapper;

    @GET()
    @Path("")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "DeepL Free API, Made by sjlleo and missuo. Go to /translate with POST. http://github.com/OwO-Network/DeepLX";
    }

    @POST()
    @Path("translate")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletableFuture<DeepLXDTO.ResponseData> handleTranslateAsync(@QueryParam("asynId") String asynId, DeepLXDTO.RequestData reqData) {
        CompletableFuture<DeepLXDTO.ResponseData> responseFuture = new CompletableFuture<>();
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
            Response.status(Response.Status.BAD_REQUEST).build();
            responseFuture.complete(
                    new DeepLXDTO.ResponseData.Error() {{
                setMessage("TEXT IS NULL");
                setCode(Response.Status.BAD_REQUEST.getStatusCode());
            }});
            return responseFuture;
        }
        boolean isSingle = reqData instanceof DeepLXDTO.RequestData.Single;
        if (asynId == null) {
            deepLClient.translate(reqData.getSourceLang(), reqData.getTargetLang(), transTexts, Json::encode)
                    .whenComplete((deepLRespData, throwable) -> {
                        if (throwable != null) {
                            throw new RuntimeException(throwable);
                        }
                        DeepLXDTO.ResponseData responseData = extractDeepLXResponseData(deepLRespData, 0, transTexts.size(), isSingle);
                        Response.status(Response.Status.fromStatusCode(responseData.getCode())).build();
                        responseFuture.complete(responseData);
                    });
        } else {
            AsynTranslateJob asynTranslateJob = new AsynTranslateJob() {{
                setAsynId(asynId);
                setRequestData(reqData);
                setResponseFuture(responseFuture);
            }};
            asycTranslateJobList.add(asynTranslateJob);
            responseFuture.whenComplete((responseData, throwable) -> {
                Response.status(Response.Status.fromStatusCode(responseData.getCode())).build();
//                responseFuture.complete(responseData);
            });
        }

        return responseFuture;
    }

    @Scheduled(every = "1s")
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

            deepLClient.translate(sourceLang, targetLang, transTexts, Json::encode)
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
                                    .orElse(Response.Status.fromStatusCode(errorData.getHttpStatusCode()).getReasonPhrase())
                    );
            return new DeepLXDTO.ResponseData.Error() {{
                setCode(errorData.getHttpStatusCode());
                setMessage(reson);
            }};
        }

        DeepLDTO.ResponseData.ResultData respData = (DeepLDTO.ResponseData.ResultData) deepLRespData;
        if (isSingle) {
            return new DeepLXDTO.ResponseData.Single() {{
                setCode(Response.Status.OK.getStatusCode());
                setId(respData.getId());
                setData(respData.getResult().getTexts().get(beginIndex).getText());
                setAlternatives(respData.getResult().getTexts().get(beginIndex).getAlternatives().stream()
                        .map(alt -> alt.getText())
                        .collect(Collectors.toList()));
            }};
        } else {
            return new DeepLXDTO.ResponseData.Batch() {{
                setCode(Response.Status.OK.getStatusCode());
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

}