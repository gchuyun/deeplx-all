package com.gchuyun.deeplx;

import lombok.Data;

import java.util.concurrent.CompletableFuture;

@Data
public class AsynTranslateJob {
    private String asynId;
    private DeepLXDTO.RequestData requestData;
    private CompletableFuture<DeepLXDTO.ResponseData> responseFuture;
}
