package com.syswonder.roboguide.hub;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.AbstractAsyncStub;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import roboguide.node.v0_4.RoboguideNode;

/**
 * Formal gRPC service descriptor and client stub for roboguide.node.v0_4.RoboGuideNodeProtocol.
 * Provides the bidirectional streaming NodeSession RPC definition.
 */
public final class RoboGuideNodeProtocolGrpc {
    public static final String SERVICE_NAME = "roboguide.node.v0_4.RoboGuideNodeProtocol";

    private static volatile MethodDescriptor<RoboguideNode.NodeMessage, RoboguideNode.ServerMessage> getNodeSessionMethod;

    public static MethodDescriptor<RoboguideNode.NodeMessage, RoboguideNode.ServerMessage> getNodeSessionMethod() {
        MethodDescriptor<RoboguideNode.NodeMessage, RoboguideNode.ServerMessage> method = getNodeSessionMethod;
        if (method == null) {
            synchronized (RoboGuideNodeProtocolGrpc.class) {
                method = getNodeSessionMethod;
                if (method == null) {
                    getNodeSessionMethod = method = MethodDescriptor.<RoboguideNode.NodeMessage, RoboguideNode.ServerMessage>newBuilder()
                            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "NodeSession"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(ProtoUtils.marshaller(RoboguideNode.NodeMessage.getDefaultInstance()))
                            .setResponseMarshaller(ProtoUtils.marshaller(RoboguideNode.ServerMessage.getDefaultInstance()))
                            .build();
                }
            }
        }
        return method;
    }

    public static RoboGuideNodeProtocolStub newStub(Channel channel) {
        return new RoboGuideNodeProtocolStub(channel, CallOptions.DEFAULT);
    }

    public static class RoboGuideNodeProtocolStub extends AbstractAsyncStub<RoboGuideNodeProtocolStub> {
        protected RoboGuideNodeProtocolStub(Channel channel, CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected RoboGuideNodeProtocolStub build(Channel channel, CallOptions callOptions) {
            return new RoboGuideNodeProtocolStub(channel, callOptions);
        }

        public StreamObserver<RoboguideNode.NodeMessage> nodeSession(
                StreamObserver<RoboguideNode.ServerMessage> responseObserver) {
            return ClientCalls.asyncBidiStreamingCall(
                    getChannel().newCall(getNodeSessionMethod(), getCallOptions()), responseObserver);
        }
    }

    private RoboGuideNodeProtocolGrpc() {}
}
