package com.yanan.framework.boot;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target({ TYPE })
public @interface PluginBoot {
	/**
	 * host
	 * @return
	 */
	String host() default "localhost";
	/**
	 * 端口
	 * @return
	 */
	int port() default 8080;
	/**
	 * 上下文类
	 * @return
	 */
	Class<?> contextClass() default PluginBoot.class;
	/**
	 * 默认使用http协议
	 * @return
	 */
	String httpProtocol() default HttpProtocol.http11Nio;
	/**
	 * 是否应用WebApp
	 * @return
	 */
	boolean enableWebApp() default true;
	/**
	 * 项目目录
	 * @return
	 */
	String appBase() default "user.dir";
	/**
	 * tomcat自身目录
	 * @return
	 */
	String baseDir() default ".";
	/**
	 * 升级协议
	 * @return
	 */
	String[] upgradeProtocol() default {};
	
	
}
