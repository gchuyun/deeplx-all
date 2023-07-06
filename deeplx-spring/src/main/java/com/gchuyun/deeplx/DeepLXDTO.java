package com.gchuyun.deeplx;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.util.List;

public abstract class DeepLXDTO {

    @Data
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type",
            defaultImpl = RequestData.Single.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value=RequestData.Single.class,name = "single"),
            @JsonSubTypes.Type(value=RequestData.Batch.class,name = "batch")})
    public abstract static class RequestData {
        @JsonProperty("source_lang")
        private String sourceLang;
        @JsonProperty("target_lang")
        private String targetLang;

        @Data
        public static class Single extends RequestData {
            @JsonProperty("text")
            private String transText;
        }
        @Data
        public static class Batch extends RequestData {
            @JsonProperty("texts")
            private List<String> transTexts;
        }
    }

    @Data
    public abstract static class ResponseData {
        @JsonProperty("code")
        private int code;

        @Data
        public static class Single extends ResponseData {
            @JsonProperty("id")
            private long id;
            @JsonProperty("data")
            private String data;
            @JsonProperty("alternatives")
            private List<String> alternatives;
        }
        @Data
        public static class Batch extends ResponseData {
            @JsonProperty("id")
            private long id;
            @JsonProperty("datas")
            private List<TranlateText> datas;

            @Data
            public static class TranlateText {
                @JsonProperty("data")
                private String data;
                @JsonProperty("alternatives")
                private List<String> alternatives;
            }
        }
        @Data
        public static class Error extends ResponseData {
            @JsonProperty("message")
            private String message;
        }
    }
}
