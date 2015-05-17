/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.socket.config.annotation;

import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpSessionScope;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.config.AbstractMessageBrokerConfiguration;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.user.UserSessionRegistryAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketAnnotationMethodMessageHandler;

/**
 * Extends {@link AbstractMessageBrokerConfiguration} and adds configuration for
 * receiving and responding to STOMP messages from WebSocket clients.
 *
 * <p>Typically used in conjunction with
 * {@link EnableWebSocketMessageBroker @EnableWebSocketMessageBroker} but can
 * also be extended directly.
 *
 * @author Rossen Stoyanchev
 * @author Artem Bilan
 * @since 4.0
 */
public abstract class WebSocketMessageBrokerConfigurationSupport extends AbstractMessageBrokerConfiguration {

	private WebSocketTransportRegistration transportRegistration;


	@Override
	protected SimpAnnotationMethodMessageHandler createAnnotationMethodMessageHandler() {
		return new WebSocketAnnotationMethodMessageHandler(clientInboundChannel(),
				clientOutboundChannel(), brokerMessagingTemplate());
	}

	@Bean
	public HandlerMapping stompWebSocketHandlerMapping() {
		WebSocketHandler handler = decorateWebSocketHandler(subProtocolWebSocketHandler());
		WebSocketTransportRegistration transport = getTransportRegistration();
		ThreadPoolTaskScheduler scheduler = messageBrokerTaskScheduler();
		WebMvcStompEndpointRegistry registry = new WebMvcStompEndpointRegistry(handler, transport, scheduler);
		registry.setApplicationContext(getApplicationContext());
		registerStompEndpoints(registry);
		return registry.getHandlerMapping();
	}

	@Bean
	public WebSocketHandler subProtocolWebSocketHandler() {
		return new SubProtocolWebSocketHandler(clientInboundChannel(), clientOutboundChannel());
	}

	protected WebSocketHandler decorateWebSocketHandler(WebSocketHandler handler) {
		for (WebSocketHandlerDecoratorFactory factory : getTransportRegistration().getDecoratorFactories()) {
			handler = factory.decorate(handler);
		}
		return handler;
	}

	protected final WebSocketTransportRegistration getTransportRegistration() {
		if (this.transportRegistration == null) {
			this.transportRegistration = new WebSocketTransportRegistration();
			configureWebSocketTransport(this.transportRegistration);
		}
		return this.transportRegistration;
	}

	protected void configureWebSocketTransport(WebSocketTransportRegistration registry) {
	}

	@Override
	@SuppressWarnings("deprecation")
	protected SimpUserRegistry createLocalUserRegistry() {
		org.springframework.messaging.simp.user.UserSessionRegistry sessionRegistry = userSessionRegistry();
		if (sessionRegistry == null) {
			return new DefaultSimpUserRegistry();
		}
		else {
			return (userSessionRegistry() instanceof SimpUserRegistry ?
					(SimpUserRegistry) userSessionRegistry() : new UserSessionRegistryAdapter(sessionRegistry));
		}
	}

	protected abstract void registerStompEndpoints(StompEndpointRegistry registry);

	@Bean
	public static CustomScopeConfigurer webSocketScopeConfigurer() {
		CustomScopeConfigurer configurer = new CustomScopeConfigurer();
		configurer.addScope("websocket", new SimpSessionScope());
		return configurer;
	}

	@Bean
	public WebSocketMessageBrokerStats webSocketMessageBrokerStats() {
		AbstractBrokerMessageHandler relayBean = stompBrokerRelayMessageHandler();
		StompBrokerRelayMessageHandler brokerRelay = (relayBean instanceof StompBrokerRelayMessageHandler ?
				(StompBrokerRelayMessageHandler) relayBean : null);

		// Ensure STOMP endpoints are registered
		stompWebSocketHandlerMapping();

		WebSocketMessageBrokerStats stats = new WebSocketMessageBrokerStats();
		stats.setSubProtocolWebSocketHandler((SubProtocolWebSocketHandler) subProtocolWebSocketHandler());
		stats.setStompBrokerRelay(brokerRelay);
		stats.setInboundChannelExecutor(clientInboundChannelExecutor());
		stats.setOutboundChannelExecutor(clientOutboundChannelExecutor());
		stats.setSockJsTaskScheduler(messageBrokerTaskScheduler());
		return stats;
	}

	@Override
	protected MappingJackson2MessageConverter createJacksonConverter() {
		MappingJackson2MessageConverter messageConverter = super.createJacksonConverter();
		// Use Jackson builder in order to have JSR-310 and Joda-Time modules registered automatically
		messageConverter.setObjectMapper(Jackson2ObjectMapperBuilder.json()
				.applicationContext(this.getApplicationContext()).build());
		return messageConverter;
	}

}
