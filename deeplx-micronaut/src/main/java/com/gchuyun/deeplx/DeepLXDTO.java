package com.gchuyun.deeplx;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.ReflectiveAccess;
import lombok.*;
import java.util.List;

public abstract class DeepLXDTO {

    @ReflectiveAccess
    @Data
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "type",
            defaultImpl = RequestData.Single.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value=RequestData.Single.class,name = "single"),
            @JsonSubTypes.Type(value=RequestData.Batch.class,name = "batch")})
    public abstract static class RequestData {
        @JsonProperty("type")
        private String type;
        @JsonProperty("source_lang")
        private String sourceLang;
        @JsonProperty("target_lang")
        private String targetLang;

        @ReflectiveAccess
        @Data
        public static class Single extends RequestData {
            @JsonProperty("text")
            private String transText;
        }
        @ReflectiveAccess
        @Data
        public static class Batch extends RequestData {
            @JsonProperty("texts")
            private List<String> transTexts;
        }
    }

    @ReflectiveAccess
    @Data
    public abstract static class ResponseData {
        @JsonProperty("code")
        private int code;

        @ReflectiveAccess
        @Data
        public static class Single extends ResponseData {
            @JsonProperty("id")
            private long id;
            @JsonProperty("data")
            private String data;
            @JsonProperty("alternatives")
            private List<String> alternatives;
        }
        @ReflectiveAccess
        @Data
        public static class Batch extends ResponseData {
            @JsonProperty("id")
            private long id;
            @JsonProperty("datas")
            private List<TranlateText> datas;

            @ReflectiveAccess
            @Data
            public static class TranlateText {
                @JsonProperty("data")
                private String data;
                @JsonProperty("alternatives")
                private List<String> alternatives;
            }
        }
        @ReflectiveAccess
        @Data
        public static class Error extends ResponseData {
            @JsonProperty("message")
            private String message;
        }
    }
}
