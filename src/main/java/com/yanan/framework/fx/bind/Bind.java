package com.yanan.framework.fx.bind;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.yanan.framework.fx.bind.conver.PropertyAdapter;

/**
 * 引导参数设置
 * @author yanan
 *
 */
@Repeatable(BindGroup.class)
@Retention(RUNTIME)
@Target({ ElementType.FIELD })
public @interface Bind {
	String property() default "";

	String target();
	
	String adapter() default ""; 
	
	@SuppressWarnings("rawtypes")
	Class<? extends PropertyAdapter> adapterClass() default PropertyAdapter.class; 
}