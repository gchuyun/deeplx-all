package com.gchuyun.deeplx;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Client(value="https://www2.deepl.com/jsonrpc", errorType=DeepLDTO.ResponseData.ErrorData.class)
@Header(name="Content-Type", value="application/json")
@Header(name="Accept", value="*/*")
@Header(name="x-app-os-name", value="iOS")
@Header(name="x-app-os-version", value="16.3.0")
@Header(name="Accept-Language", value="en-US,en;q=0.9")
// @Header(name="Accept-Encoding", value="gzip, deflate, br")
@Header(name="Accept-Encoding", value="gzip")
@Header(name="x-app-device", value="iPhone13,2")
@Header(name="User-Agent", value="DeepL-iOS/2.6.0 iOS 16.3.0 (iPhone13,2)")
@Header(name="x-app-build", value="353933")
@Header(name="x-app-version", value="2.6")
@Header(name="Connection", value="keep-alive")
public interface DeepLClient {
    @Post
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CompletableFuture<DeepLDTO.ResponseData.ResultData> translate(@Body String body);

    Logger LOGGER = LoggerFactory.getLogger(DeepLClient.class);
    default CompletableFuture<DeepLDTO.ResponseData> translate(String sourceLang, String targetLang, List<String> translateTexts, ObjectMapper objectMapper) {
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
            postStr = objectMapper.writeValueAsString(deepLReqData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if ((id + 5) % 29 == 0 || (id + 3) % 13 == 0) {
            postStr = postStr.replace("\"method\":\"", "\"method\" : \"");
        } else {
            postStr = postStr.replace("\"method\":\"", "\"method\": \"");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("deepl request: {}", postStr);
        }

        String finalPostStr = postStr;
        CompletableFuture<DeepLDTO.ResponseData> deepLRespDataFuture = new CompletableFuture<>();
        this.translate(finalPostStr).whenComplete((deepLRespData, throwable) -> {
            if (throwable != null) {
                if (throwable instanceof HttpClientResponseException) {
                    HttpClientResponseException exception = (HttpClientResponseException) throwable;
                    DeepLDTO.ResponseData.ErrorData respErrorData = exception.getResponse().getBody(DeepLDTO.ResponseData.ErrorData.class).get();

                    LOGGER.warn("deepl request: {}", finalPostStr);
                    LOGGER.warn("deepl error: {}", objectMapper.writeValueAsString(respErrorData));

                    if (respErrorData != null) {
                        respErrorData.setHttpStatusCode(exception.getStatus().getCode());
                    }
                    deepLRespDataFuture.complete(respErrorData);
                    return;
                }
                throw new RuntimeException(throwable);
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("deepl response: {}", objectMapper.writeValueAsString(deepLRespData));
            }
            deepLRespDataFuture.complete(deepLRespData);
        });
        return deepLRespDataFuture;
    }
    default DeepLDTO.ReqestData initData(String srcLang, String tgtLang) {
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

    default long getICount(String translateText) {
        return translateText.chars().filter(ch -> ch == 'i').count();
    }

    default long getRandomId() {
        Random rand = new Random(System.currentTimeMillis());
        long num = rand.nextInt(99999) + 8300000;
        return num * 1000;
    }

    default long getICountTimeStamp(long iCount) {
        long timeMillis = System.currentTimeMillis();
        if (iCount != 0) {
            iCount = iCount + 1;
            return timeMillis - timeMillis % iCount + iCount;
        } else {
            return timeMillis;
        }
    }

    interface ObjectMapper {
        String writeValueAsString(Object o);
    }
}
