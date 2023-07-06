package com.gchuyun.deeplx;

import io.smallrye.common.annotation.Blocking;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Path("/jsonrpc")
@RegisterRestClient
@RegisterClientHeaders(DeepLService.RequestHeaderFactory.class)
@RegisterProvider(DeepLService.DeepLExceptionMapper.class)
public interface DeepLService {
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CompletionStage<DeepLDTO.ResponseData.ResultData> translate(String body);

    @ApplicationScoped
    public static class RequestHeaderFactory implements ClientHeadersFactory {
        @Override
        public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders, MultivaluedMap<String, String> clientOutgoingHeaders) {
            MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
            result.add("Content-Type", "application/json");
            result.add("Accept", "*/*");
            result.add("x-app-os-name", "iOS");
            result.add("x-app-os-version", "16.3.0");
            result.add("Accept-Language", "en-US,en;q=0.9");
            result.add("Accept-Encoding", "gzip, deflate, br");
            result.add("x-app-device", "iPhone13,2");
            result.add("User-Agent", "DeepL-iOS/2.6.0 iOS 16.3.0 (iPhone13,2)");
            result.add("x-app-build", "353933");
            result.add("x-app-version", "2.6");
            result.add("Connection", "keep-alive");
            return result;
        }
    }

    @Blocking
    public static class DeepLExceptionMapper implements ResponseExceptionMapper<DeepLException> {

        @Override
        public DeepLException toThrowable(Response response) {
            DeepLDTO.ResponseData.ErrorData errorData = response.readEntity(DeepLDTO.ResponseData.ErrorData.class);
            if (errorData != null) {
                errorData.setHttpStatusCode(response.getStatus());
            }
            return new DeepLException(errorData);
        }
    }

    @Data
    @AllArgsConstructor
    public static class DeepLException extends RuntimeException {
        private DeepLDTO.ResponseData.ErrorData errorData;
    }
}
