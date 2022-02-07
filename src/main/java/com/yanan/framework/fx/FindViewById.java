package com.yanan.framework.fx;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 找到视图
 * @author yanan
 *
 */
@Retention(RUNTIME)
@Target({ ElementType.FIELD })
public @interface FindViewById {
	String value() default "";
}