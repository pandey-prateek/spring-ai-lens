package io.ailens.springailens.interceptor;

import io.ailens.springailens.store.RingBufferEventStore;
import io.ailens.springailens.model.AiCallEvent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiLensInterceptorTest {

    @Test
    void interceptorCapturesCallEvent() {
        // build the store and interceptor
        RingBufferEventStore store = new RingBufferEventStore(10);
        AiLensInterceptor interceptor = new AiLensInterceptor(store);

        // mock ChatModel returning a simple response
        ChatModel mockModel = mock(ChatModel.class);
        ChatResponse mockResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Paris"))
        ));
        when(mockModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // wrap mock with AOP proxy so our aspect fires
        AspectJProxyFactory factory = new AspectJProxyFactory(mockModel);
        factory.addAspect(interceptor);
        ChatModel proxied = factory.getProxy();

        // make the call
        proxied.call(new Prompt("What is the capital of France?"));

        // assert event was captured
        List<AiCallEvent> events = store.getAll();
        assertThat(events).hasSize(1);

        AiCallEvent event = events.get(0);
        assertThat(event.prompt()).isEqualTo("What is the capital of France?");
        assertThat(event.response()).isEqualTo("Paris");
        assertThat(event.latencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(event.id()).isNotNull();
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void ringBufferDropsOldestWhenFull() {
        RingBufferEventStore store = new RingBufferEventStore(2);

        ChatModel mockModel = mock(ChatModel.class);
        ChatResponse mockResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("answer"))
        ));
        when(mockModel.call(any(Prompt.class))).thenReturn(mockResponse);

        AspectJProxyFactory factory = new AspectJProxyFactory(mockModel);
        factory.addAspect(new AiLensInterceptor(store));
        ChatModel proxied = factory.getProxy();

        proxied.call(new Prompt("question 1"));
        proxied.call(new Prompt("question 2"));
        proxied.call(new Prompt("question 3")); // should evict question 1

        List<AiCallEvent> events = store.getAll();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).prompt()).isEqualTo("question 2");
        assertThat(events.get(1).prompt()).isEqualTo("question 3");
    }
}