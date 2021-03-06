package com.yanan.framework.boot.web;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 定义一个WebApp
 * @author yanan
 *
 */
@Retention(RUNTIME)
@Target({ TYPE })
@Repeatable(WebAppGroups.class)
public @interface WebApp {
	/**
	 * WebApp请求上下文
	 * @return
	 */
	String contextPath();
	/**
	 * WebApp的资源路径
	 * @return
	 */
	String docBase();
}