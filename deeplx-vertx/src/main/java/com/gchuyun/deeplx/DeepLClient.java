package com.gchuyun.deeplx;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DeepLClient {
  private Vertx vertx;
  public DeepLClient(Vertx vertx) {
    this.vertx = vertx;
  }

  Logger LOGGER = LoggerFactory.getLogger(DeepLClient.class);
  public CompletableFuture<DeepLDTO.ResponseData> translate(String sourceLang, String targetLang, List<String> translateTexts) {
    if (sourceLang == null || sourceLang.trim().isEmpty()) {
      sourceLang = "AUTO";
    }
    if (targetLang == null || targetLang.trim().isEmpty()) {
      if (translateTexts.stream().anyMatch(text -> text.matches(".*[\u4E00-\u9FA5]+.*"))) {
        targetLang = "EN";
      } else {
        targetLang = "ZH";
      }
    }

    long id = getRandomId();
    DeepLDTO.ReqestData deepLReqData = initData(sourceLang, targetLang);
    deepLReqData.setId(id);
    deepLReqData.getParams().setTexts(translateTexts.stream()
      .map(t -> new DeepLDTO.ReqestData.Text() {{
        text = t;
        requestAlternatives = 3;
      }}).collect(Collectors.toList()));
    deepLReqData.getParams().setTimestamp(
      getICountTimeStamp(translateTexts.stream().mapToLong(t -> getICount(t)).sum()));

    String postStr = null;
    try {
      postStr = Json.encode(deepLReqData);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    if ((id + 5) % 29 == 0 || (id + 3) % 13 == 0) {
      postStr = postStr.replace("\"method\":\"", "\"method\" : \"");
    } else {
      postStr = postStr.replace("\"method\":\"", "\"method\": \"");
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("deepl request: " + postStr);
    }

    String finalPostStr = postStr;
    CompletableFuture<DeepLDTO.ResponseData> deepLRespDataFuture = new CompletableFuture<>();

    String url = "https://www2.deepl.com/jsonrpc";
    WebClientOptions options = new WebClientOptions().setKeepAlive(false);
    WebClient client = WebClient.create(vertx, options);

    Buffer buffer = Buffer.buffer(postStr);

    client.postAbs(url)
      .putHeader("Content-Type", "application/json")
      .putHeader("Accept", "*/*")
      .putHeader("x-app-os-name", "iOS")
      .putHeader("x-app-os-version", "16.3.0")
      .putHeader("Accept-Language", "en-US,en;q=0.9")
      .putHeader("Accept-Encoding", "gzip, deflate, br")
      .putHeader("x-app-device", "iPhone13,2")
      .putHeader("User-Agent", "DeepL-iOS/2.6.0 iOS 16.3.0 (iPhone13,2)")
      .putHeader("x-app-build", "353933")
      .putHeader("x-app-version", "2.6")
      .putHeader("Connection", "keep-alive")
      .sendBuffer(buffer)
      .onSuccess(response -> {
        int httpStatusCode = response.statusCode();
        JsonObject respJson = response.bodyAsJsonObject();

        if (httpStatusCode >= 400) {
          DeepLDTO.ResponseData.ErrorData respErrorData = respJson.mapTo(DeepLDTO.ResponseData.ErrorData.class);

          LOGGER.warn("deepl request: " + finalPostStr);
          LOGGER.warn("deepl error: " + Json.encode(respErrorData));

          if (respErrorData != null) {
            respErrorData.setHttpStatusCode(httpStatusCode);
          }
          deepLRespDataFuture.complete(respErrorData);
          return;
        }
        DeepLDTO.ResponseData.ResultData deepLRespData = respJson.mapTo(DeepLDTO.ResponseData.ResultData.class);
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("deepl response: " + Json.encode(deepLRespData));
        }
        deepLRespDataFuture.complete(deepLRespData);

      }).onFailure(err -> LOGGER.error(err.getMessage()));
    return deepLRespDataFuture;
  }
  public DeepLDTO.ReqestData initData(String srcLang, String tgtLang) {
    return new DeepLDTO.ReqestData() {{
      setJsonrpc("2.0");
      setMethod("LMT_handle_texts");
      setParams(new DeepLDTO.ReqestData.Params() {{
        setSplitting("newlines");
        setLang(new DeepLDTO.ReqestData.Lang() {{
          setSourceLangUserSelected(srcLang);
          setTargetLang(tgtLang);
        }});
        setCommonJobParams(new DeepLDTO.ReqestData.CommonJobParams() {{
          setWasSpoken(false);
          setTranscribeAS("");
        }});
      }});
    }};
  }

  public long getICount(String translateText) {
    return translateText.chars().filter(ch -> ch == 'i').count();
  }

  public long getRandomId() {
    Random rand = new Random(System.currentTimeMillis());
    long num = rand.nextInt(99999) + 8300000;
    return num * 1000;
  }

  public long getICountTimeStamp(long iCount) {
    long timeMillis = System.currentTimeMillis();
    if (iCount != 0) {
      iCount = iCount + 1;
      return timeMillis - timeMillis % iCount + iCount;
    } else {
      return timeMillis;
    }
  }
}
