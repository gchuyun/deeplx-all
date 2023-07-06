package com.gchuyun.deeplx;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {
  private static final Logger LOGGER = Logger.getLogger(MainVerticle.class.getName());
  private final Queue<AsynTranslateJob> asycTranslateJobList = new ConcurrentLinkedQueue<>();
  private DeepLClient deepLClient;

  @Override
  public void start() {
    deepLClient = new DeepLClient(vertx);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.get("/").handler(this::handleIntroduce);
    router.post("/translate").handler(this::handleTranslate);

    vertx.setPeriodic(1000, this::handlePeriodicTranslateAsync);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(1188)
      .onSuccess(server -> {
        LOGGER.info("DeepL X has been successfully launched! Listening on 0.0.0.0:1188\nMade by sjlleo and missuo.");
      });
  }

  public void handleIntroduce(RoutingContext context) {
    context.json(new DeepLXDTO.ResponseData.Error() {{
      setMessage("DeepL Free API, Made by sjlleo and missuo. Go to /translate with POST. http://github.com/OwO-Network/DeepLX");
      setCode(HttpResponseStatus.OK.code());
    }});
  }

  public void handleTranslate(RoutingContext context) {
    String asynId = context.queryParams().get("asynId");
    DeepLXDTO.RequestData reqData = context.body().asPojo(DeepLXDTO.RequestData.class);
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
      context.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code());
      context.json(new DeepLXDTO.ResponseData.Error() {{
        setMessage("TEXT IS NULL");
        setCode(HttpResponseStatus.BAD_REQUEST.code());
      }});
      return;
    }
    boolean isSingle = reqData instanceof DeepLXDTO.RequestData.Single;
    if (asynId == null) {
      deepLClient.translate(reqData.getSourceLang(), reqData.getTargetLang(), transTexts)
        .whenComplete((deepLRespData, throwable) -> {
          if (throwable != null) {
            throw new RuntimeException(throwable);
          }
          DeepLXDTO.ResponseData responseData = extractDeepLXResponseData(deepLRespData, 0, transTexts.size(), isSingle);
          context.response().setStatusCode(responseData.getCode());
          context.json(responseData);
        });
    } else {
      CompletableFuture<DeepLXDTO.ResponseData> responseFuture = new CompletableFuture<>();
      AsynTranslateJob asynTranslateJob = new AsynTranslateJob() {{
        setAsynId(asynId);
        setRequestData(reqData);
        setResponseFuture(responseFuture);
      }};
      asycTranslateJobList.add(asynTranslateJob);
      responseFuture.whenComplete((responseData, throwable) -> {
        context.response().setStatusCode(responseData.getCode());
        context.json(responseData);
      });
    }
  }

  public void handlePeriodicTranslateAsync(long id) {
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
              .orElse(HttpResponseStatus.valueOf(errorData.getHttpStatusCode()).reasonPhrase())
          );
        return new DeepLXDTO.ResponseData.Error() {{
          setCode(errorData.getHttpStatusCode());
          setMessage(reson);
        }};
      }

      DeepLDTO.ResponseData.ResultData respData = (DeepLDTO.ResponseData.ResultData) deepLRespData;
      if (isSingle) {
        return new DeepLXDTO.ResponseData.Single() {{
          setCode(HttpResponseStatus.OK.code());
          setId(respData.getId());
          setData(respData.getResult().getTexts().get(beginIndex).getText());
          setAlternatives(respData.getResult().getTexts().get(beginIndex).getAlternatives().stream()
            .map(alt -> alt.getText())
            .collect(Collectors.toList()));
        }};
      } else {
        return new DeepLXDTO.ResponseData.Batch() {{
          setCode(HttpResponseStatus.OK.code());
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
