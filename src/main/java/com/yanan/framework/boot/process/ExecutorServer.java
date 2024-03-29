package com.yanan.framework.boot.process;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;

import com.yanan.framework.plugin.annotations.Register;
import com.yanan.framework.plugin.annotations.Service;
import com.yanan.framework.plugin.autowired.enviroment.Variable;


@Variable(value = "plugin.boot.executor")
@Register
public class ExecutorServer {
	private boolean enable;
	private int process = 10;
	private int maxProcess = 100;
	private int taskSize = 2<<10;
	private int timeout = 60;
	@Service
	private Logger logger;
	/**
	 * 任务处理线程
	 */
	private ExecutorService processExecutor;
	public int getProcess() {
		return process;
	}
	public void setProcess(int process) {
		this.process = process;
	}
	public int getMaxProcess() {
		return maxProcess;
	}
	public void setMaxProcess(int maxProcess) {
		this.maxProcess = maxProcess;
	}
	public int getTaskSize() {
		return taskSize;
	}
	public void setTaskSize(int taskSize) {
		this.taskSize = taskSize;
	}
	public int getTimeout() {
		return timeout;
	}
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	public ExecutorService getProcessExecutor() {
		return processExecutor;
	}
	public void setProcessExecutor(ExecutorService processExecutor) {
		this.processExecutor = processExecutor;
	}
	@PostConstruct
	public void createProcessExector() {
		logger.info("init boot executor server,status:"+enable);
		if(!enable) {
			return;
		}
		logger.info("executor process:"+process);
		logger.info("executor maxProcess:"+maxProcess);
		logger.info("executor taskSize:"+taskSize);
		logger.info("executor timeout:"+timeout);
		BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<Runnable>(taskSize);
		if(this.process>0){
			if(maxProcess<process)
				maxProcess = process<<1;
			this.processExecutor = new ThreadPoolExecutor(process,
					maxProcess, 
					timeout, 
					TimeUnit.MILLISECONDS, 
					blockingQueue,
					new ExecutorThreadFactory(this),
					new ThreadPoolExecutor.CallerRunsPolicy());
		}else {
			this.processExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
					Runtime.getRuntime().availableProcessors()<<1,
					timeout,
					TimeUnit.MILLISECONDS,
					blockingQueue,
					new ExecutorThreadFactory(this),
					new ThreadPoolExecutor.CallerRunsPolicy());
		}
	}
	public void execute(AbstractProcess messageProcesser) {
		if(process != 0){
			this.processExecutor.execute(messageProcesser);
		}else{
			messageProcesser.run();
		}
	}
	public void executeProcess(AbstractProcess process) {
		this.processExecutor.execute(process);
	}
	public void execute(Runnable process) {
		this.processExecutor.execute(process);
	}
	public void shutdown() {
		processExecutor.shutdown();
	}
	public boolean isShutdown() {
		return processExecutor.isShutdown();
	}
}
