package com.gchuyun.deeplx;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.ReflectiveAccess;
import lombok.Data;

import java.util.List;
import java.util.Map;

public abstract class DeepLDTO {

    @ReflectiveAccess
    @Data
    public static class ReqestData {
        @JsonProperty("jsonrpc")
        private String jsonrpc;
        @JsonProperty("method")
        private String method;
        @JsonProperty("id")
        private long id;
        @JsonProperty("params")
        private Params params;

        @ReflectiveAccess
        @Data
        public static class Params {
            @JsonProperty("texts")
            private List<Text> texts;
            @JsonProperty("splitting")
            private String splitting;
            @JsonProperty("lang")
            private Lang lang;
            @JsonProperty("timestamp")
            private long timestamp;
            @JsonProperty("commonJobParams")
            private CommonJobParams commonJobParams;
        }

        @ReflectiveAccess
        @Data
        public static class Lang {
            @JsonProperty("source_lang_user_selected")
            public String sourceLangUserSelected;
            @JsonProperty("target_lang")
            public String targetLang;
        }

        @ReflectiveAccess
        @Data
        public static class CommonJobParams {
            @JsonProperty("wasSpoken")
            public boolean wasSpoken;
            @JsonProperty("transcribe_as")
            public String transcribeAS;
//    @JsonProperty("regionalVariant")
//    public String regionalVariant;
        }

        @ReflectiveAccess
        @Data
        public static class Text {
            @JsonProperty("text")
            public String text;
            @JsonProperty("requestAlternatives")
            public int requestAlternatives;
        }
    }

    @ReflectiveAccess
    @Data
    public abstract static class ResponseData {
        @JsonProperty("jsonrpc")
        private String jsonrpc;
        @JsonProperty("id")
        private long id;

        @ReflectiveAccess
        @Data
        public static class ResultData extends ResponseData {
            @JsonProperty("result")
            private Result result;

            @ReflectiveAccess
            @Data
            public static class Result {
                @JsonProperty("texts")
                private List<Text> texts;
                @JsonProperty("lang")
                private String lang;
                @JsonProperty("lang_is_confident")
                private boolean langIsConfident;
                @JsonProperty("detectedLanguages")
                private Map<String, Float> detectedLanguages;
            }

            @ReflectiveAccess
            @Data
            public static class Text {
                @JsonProperty("text")
                private String text;
                @JsonProperty("alternatives")
                private List<Alternative> alternatives;
            }

            @ReflectiveAccess
            @Data
            public static class Alternative {
                @JsonProperty("text")
                private String text;
            }
        }

        @ReflectiveAccess
        @Data
        public static class ErrorData extends ResponseData {
            //    {"jsonrpc":"2.0","id":-242107591,"error":{"code":-32600,"message":"Invalid Request","data":{"what":"Invalid target_lang."}}}
            @JsonIgnore
            private int httpStatusCode;
            @JsonProperty("error")
            private Error error;

            @ReflectiveAccess
            @Data
            public static class Error {
                @JsonProperty("code")
                private long code;
                @JsonProperty("message")
                private String message;
                @JsonProperty("data")
                private Reason data;
            }

            @ReflectiveAccess
            @Data
            public static class Reason {
                @JsonProperty("what")
                private String what;
            }
        }
    }
}
