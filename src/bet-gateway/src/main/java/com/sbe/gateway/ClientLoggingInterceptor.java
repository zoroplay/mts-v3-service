package com.sbe.gateway;

import io.grpc.*;

public class ClientLoggingInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,   // <-- THIS 'method'
            CallOptions callOptions,
            Channel next) {

        System.out.println("[gRPC] calling " + method.getFullMethodName());

        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(
                        new ForwardingClientCallListener
                                .SimpleForwardingClientCallListener<>(responseListener) {
                            @Override
                            public void onHeaders(Metadata headers) {
                                System.out.println("[gRPC] headers: " + headers);
                                super.onHeaders(headers);
                            }

                            @Override
                            public void onClose(Status status, Metadata trailers) {
                                System.out.println("[gRPC] closed with status: " + status);
                                super.onClose(status, trailers);
                            }
                        },
                        headers);
            }
        };
    }
}

