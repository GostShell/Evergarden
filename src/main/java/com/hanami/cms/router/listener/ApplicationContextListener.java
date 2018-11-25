package com.hanami.cms.router.listener;

import com.hanami.cms.router.RouteBuilder;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;


@Component
public class ApplicationContextListener {

	private Logger       logger;
	private RouteBuilder routeBuilder;

	@Autowired
	public ApplicationContextListener(Logger logger, RouteBuilder routeBuilder) {
		this.logger = logger;
		this.routeBuilder = routeBuilder;
	}

	@EventListener
	public void handleContextStart(ContextRefreshedEvent event) {
		try {
			logger.info("All route build successfully");
			//return routeBuilder.build();
		} catch (Throwable e) {
			e.printStackTrace();
			logger.error("RouterBuilder failed to build route");
		}
	}
}
