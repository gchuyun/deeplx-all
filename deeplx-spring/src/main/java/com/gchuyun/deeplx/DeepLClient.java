package com.gchuyun.deeplx;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DeepLClient {

  @Autowired
  ObjectMapper objectMapper;

  Logger LOGGER = LoggerFactory.getLogger(DeepLClient.class);
  public Mono<DeepLDTO.ResponseData> translate(String sourceLang, String targetLang, List<String> translateTexts) {
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

    Function<Object, String> writeValueAsString = o -> {
      try {
        return objectMapper.writeValueAsString(o);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
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
      LOGGER.debug("deepl request: " + postStr);
    }

    String finalPostStr = postStr;
    return WebClient.builder()
            .baseUrl("https://www2.deepl.com")
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "*/*")
            .defaultHeader("x-app-os-name", "iOS")
            .defaultHeader("x-app-os-version", "16.3.0")
            .defaultHeader("Accept-Language", "en-US,en;q=0.9")
            .defaultHeader("Accept-Encoding", "gzip, deflate, br")
            .defaultHeader("x-app-device", "iPhone13,2")
            .defaultHeader("User-Agent", "DeepL-iOS/2.6.0 iOS 16.3.0 (iPhone13,2)")
            .defaultHeader("x-app-build", "353933")
            .defaultHeader("x-app-version", "2.6")
            .defaultHeader("Connection", "keep-alive")
            .build()
            .method(HttpMethod.POST)
            .uri("/jsonrpc")
            .bodyValue(postStr)
            .exchangeToMono(response -> {
              if (response.statusCode().value() >= 400) {
                return response.bodyToMono(DeepLDTO.ResponseData.ErrorData.class)
                        .doOnSuccess(errorData -> {
                          LOGGER.warn("deepl request: " + finalPostStr);
                          LOGGER.warn("deepl error: " + writeValueAsString.apply(errorData));

                          errorData.setHttpStatusCode(response.statusCode().value());
                        })
                        .map(o -> (DeepLDTO.ResponseData) o);
              } else {
                return response.bodyToMono(DeepLDTO.ResponseData.ResultData.class)
                        .doOnSuccess(deepLRespData -> {
                          if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("deepl response: " + writeValueAsString.apply(deepLRespData));
                          }
                        })
                        .map(o -> (DeepLDTO.ResponseData) o);
              }
            });
  }
  public DeepLDTO.ReqestData initData(String srcLang, String tgtLang) {
    return new DeepLDTO.ReqestData() {{
      setJsonrpc("2.0");
      setMethod("LMT_handle_texts");
      setParams(new Params() {{
        setSplitting("newlines");
        setLang(new Lang() {{
          setSourceLangUserSelected(srcLang);
          setTargetLang(tgtLang);
        }});
        setCommonJobParams(new CommonJobParams() {{
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
