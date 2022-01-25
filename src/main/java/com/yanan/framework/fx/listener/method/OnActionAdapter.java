package com.yanan.framework.fx.listener.method;

import java.lang.reflect.Method;

import com.yanan.framework.fx.FxApplication;
import com.yanan.framework.plugin.annotations.Register;
import com.yanan.utils.reflect.ReflectUtils;

import javafx.beans.property.ObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

@Register(attribute = "OnAction")
public class OnActionAdapter implements FxMethodListener<OnAction>{

	@Override
	public void adapter(FxApplication fxApplication, Method method,OnAction onSelected) throws Exception{
		Object view = fxApplication.findViewById(onSelected.value());
		ObjectProperty<EventHandler<ActionEvent>> objectProperty = ReflectUtils.invokeMethod(view,"onActionProperty");
		objectProperty.set(FxMethodListener.DefaultMethodAdapter.onEventListener(method,fxApplication));
	}

}
