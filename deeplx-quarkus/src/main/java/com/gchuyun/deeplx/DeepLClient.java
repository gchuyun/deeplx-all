package com.gchuyun.deeplx;

import lombok.Data;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@Data
public class DeepLClient {
    @Inject
    @RestClient
    private DeepLService deepLService;

    public static  final Logger LOGGER = LoggerFactory.getLogger(DeepLClient.class);
    public CompletableFuture<DeepLDTO.ResponseData> translate(String sourceLang, String targetLang, List<String> translateTexts, Function<Object, String> writeValueAsString) {
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
            postStr = writeValueAsString.apply(deepLReqData);
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
        this.deepLService.translate(finalPostStr).whenComplete((deepLRespData, throwable) -> {
            if (throwable != null) {
                if (throwable instanceof DeepLService.DeepLException) {
                    DeepLDTO.ResponseData.ErrorData respErrorData = ((DeepLService.DeepLException) throwable).getErrorData();

                    LOGGER.warn("deepl request: {}", finalPostStr);
                    LOGGER.warn("deepl error: {}", writeValueAsString.apply(respErrorData));

                    deepLRespDataFuture.complete(respErrorData);
                    return;
                }
                throw new RuntimeException(throwable);
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("deepl response: {}", writeValueAsString.apply(deepLRespData));
            }
            deepLRespDataFuture.complete(deepLRespData);
        });
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
