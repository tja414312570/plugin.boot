package plugin.proxy;

import java.io.IOException;
import java.io.WriteAbortedException;
import java.lang.reflect.Field;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import com.yanan.framework.ant.core.BufferReady;
import com.yanan.framework.ant.core.ByteBufferChannel;
import com.yanan.framework.ant.core.MessageSerialization;
import com.yanan.framework.ant.exception.AntMessageResolveException;
import com.yanan.framework.plugin.annotations.Register;
import com.yanan.framework.plugin.annotations.Service;
import com.yanan.utils.ByteUtils;
import com.yanan.utils.reflect.TypeToken;

import sun.misc.Cleaner;

/**
 * 
 * RPC包 线程安全 此类用来处理 半包 粘包问题 需要遵循消息协议 消息 = 帧长+帧内容 ... 帧长+帧内容格式的帧 一个链接所有流对应一个RPC包
 * 帧：一个完整的数据
 * 
 * @author yanan
 *
 */
@Register
public class ByteBufferChannelHandler<T> implements ByteBufferChannel<T>{
	/**
	 * 用来存储缓存
	 */
	private LinkedList<T> messageList;
	/**
	 * 读buffer
	 */
	private ByteBuffer readBuffer;
	/**
	 * 写数据用的buffer
	 */
	private ByteBuffer writeBuffer;
	/**
	 * 包头数据大小
	 */
	private final int PACKAGE_HEAD_LENGTH = 4;
	/**
	 * 包头缓冲
	 */
	private final byte[] packageHeadBuffer = new byte[PACKAGE_HEAD_LENGTH];
	/**
	 * 缓冲最大值
	 */
	private int maxBufferSize = 1024;
	/**
	 * 溢出包长度
	 */
	private int outflowPackageLen;
	/**
	 * 序列化工具
	 */
	@Service
	private MessageSerialization serailzation;
	private BufferReady<T> bufferReady;
	public ByteBufferChannelHandler() {
		messageList = new LinkedList<>();
//		setMaxBufferSize(config.getBufferMaxSize());
//		if (config.getBufferType() == BufferType.DIRECT) {
//			setReadBuffer(ByteBuffer.allocateDirect(config.getBufferSize()));
//			setWriteBuffer(ByteBuffer.allocateDirect(config.getBufferSize()));
//		} else {
			setReadBuffer(ByteBuffer.allocate(2048));
			setWriteBuffer(ByteBuffer.allocate(2048));
//		}
	}
	public int getOutflowPackageLen() {
		return outflowPackageLen;
	}
	public void setOutflowPackageLen(int outflowPackageLen) {
		this.outflowPackageLen = outflowPackageLen;
	}
	public void setBuffer(ByteBuffer readBuffer,ByteBuffer writeBuffer) {
		this.readBuffer = readBuffer;
		this.writeBuffer = writeBuffer;
	}
	public ByteBuffer getWriteBuffer() {
		return writeBuffer;
	}
	public void setWriteBuffer(ByteBuffer writeBuffer) {
		this.writeBuffer = writeBuffer;
	}
	public void setReadBuffer(ByteBuffer readBuffer) {
		this.readBuffer = readBuffer;
	}
	/**
	 * 获取包中的缓存类容
	 * 
	 * @return
	 */
	public ByteBuffer getReadBuffer() {
		return this.readBuffer;
	}

	/**
	 * 判断是否还有更多帧
	 * 
	 * @return
	 */
	public boolean hasMoreMessage() {
		return !this.messageList.isEmpty();
	}

	/**
	 * 获取帧数量
	 * 
	 * @param pos
	 * @return
	 */
	public int getMessageNum() {
		return this.messageList.size();
	}

	/**
	 * 读取第一帧并返回一个对象
	 * 
	 * @param msg
	 * @param type
	 * @return
	 */
	public synchronized T getMessage() {
		if (messageList.isEmpty())
			return null;
		return messageList.removeFirst();
	}
	/**
	 * 将输入流添加到包中
	 * 
	 * @param key
	 * 
	 * @param is
	 * @throws AntMessageResolveException 
	 * @throws IOException
	 */
	public synchronized void handleRead() {
		this.bufferReady.handleRead(readBuffer);
		boolean compact = false;
		// 从通道读取数据
			try {
				readBuffer.flip();
				// 缓冲区可用数据长度
				int available;
				while ((available = readBuffer.remaining()) > PACKAGE_HEAD_LENGTH) {
					int position = readBuffer.position();
					if(this.outflowPackageLen > 0) {
						if(available>this.outflowPackageLen) {
							readBuffer.position(this.outflowPackageLen);
						}else {
							this.outflowPackageLen = this.outflowPackageLen - available;
							readBuffer.position(readBuffer.limit());
							break;
						}
					}
					// 读取包头
					readBuffer.get(packageHeadBuffer);
					// 消息体长度
					int messageLen = ByteUtils.bytesToInt(packageHeadBuffer);
					// 总长度
					if (messageLen+PACKAGE_HEAD_LENGTH > this.maxBufferSize) {
						readBuffer.position(readBuffer.limit());
						this.outflowPackageLen = messageLen-available+PACKAGE_HEAD_LENGTH;
						readBuffer = ensureCapacity(writeBuffer, messageLen);
						throw new AntMessageResolveException("request package len (" + messageLen
								+ ") out of configure max buffer size (" + maxBufferSize + ")");
					}
					// 判断有效内容长度是否小于总长度
					if (available - PACKAGE_HEAD_LENGTH < messageLen) {
						// 有效内容小于总长度 半包情况
						//恢复指针位置
						readBuffer.position(position);
						//如果剩余容量小于总容量的1/3,扩容
						if(this.readBuffer.capacity() / 3 > this.readBuffer.capacity()-this.readBuffer.limit()) {
							//先压缩
							compress(readBuffer);
							//计算需要扩容的容量
							int len = calculateCapacity(readBuffer.capacity(),messageLen << 1,this.maxBufferSize);
							//扩容
							this.readBuffer = ensureCapacity(this.readBuffer,len);
						}
						//如果容量剩余小于包体长度时，也扩容
						if(messageLen > this.readBuffer.capacity()-this.readBuffer.limit()) {
							compress(readBuffer);
							int len = calculateCapacity(readBuffer.capacity(),messageLen << 1,this.maxBufferSize);
							this.readBuffer = ensureCapacity(this.readBuffer,len);
						}
						//如果指针位置太高、压缩一下
						if(this.readBuffer.capacity()-position < messageLen+PACKAGE_HEAD_LENGTH) {
							compress(readBuffer);
						}
						break;
					}
					//反序列化
					T message = serailzation.deserial(readBuffer, position, messageLen,
							new TypeToken<T>() {}.getTypeClass());
					messageList.add(message);
				}
			} finally {
				if(!compact)
					readBuffer.compact();
//					if(this.outflowPackageLen > 0) {
//						this.readBuffer.clear();
//					}
			}
			//通知消息
			T message;
			while((message = getMessage()) != null) {
				this.bufferReady.onMessage(message);
			}
	}
	/**
	 * 压缩包
	 * @param byteBuffer
	 */
	public static void compress(ByteBuffer byteBuffer) {
		int pos = 0;
		int available = byteBuffer.remaining();
		while(byteBuffer.hasRemaining()) {
			byteBuffer.put(pos++,byteBuffer.get());
		}
		byteBuffer.position(0);
		byteBuffer.limit(available);
//		byteBuffer.compact();
	}
	/**
	 * 扩容
	 * @param byteBuffer
	 * @param len
	 * @return
	 */
	public static ByteBuffer ensureCapacity(ByteBuffer byteBuffer, int len) {
		if(byteBuffer.capacity()>=len)
			return byteBuffer;
		ByteBuffer  newBuffer = 
				byteBuffer.isDirect()?
						ByteBuffer.allocateDirect(len):ByteBuffer.allocate(len);
		int pos = byteBuffer.position();
		int limit = byteBuffer.limit();
		byteBuffer.rewind();
		while(byteBuffer.hasRemaining()) {
			byte[] byts = new byte[byteBuffer.remaining()];
			byteBuffer.get(byts);
			newBuffer.put(byts);
		}
		newBuffer.position(pos);
		newBuffer.limit(limit);
		clean(byteBuffer);
		return newBuffer;
	}
	/**
	 * 清理缓存
	 * @param byteBuffer
	 */
	private static void clean(ByteBuffer byteBuffer) {
		if(byteBuffer.isDirect()) {
			Field cleanerField;
			try {
				cleanerField = byteBuffer.getClass().getDeclaredField("cleaner");
				 cleanerField.setAccessible(true);
				    Cleaner cleaner = (Cleaner) cleanerField.get(byteBuffer);
				    cleaner.clean();
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}else {
			byteBuffer.clear();
		}
	}
	/**
	 * 计算容量
	 * @param size
	 * @param minBufferSize
	 * @param maxBufferSize
	 * @return
	 */
	public static int calculateCapacity(int size, int minBufferSize,int maxBufferSize) {
		if(size < 0 || minBufferSize < 0 || maxBufferSize < 0)
			throw new IllegalArgumentException("size are not allowed to be negative");
		if(maxBufferSize > 0) {
			if(minBufferSize > maxBufferSize)
				throw new IllegalArgumentException("need buffered size("+minBufferSize+") over flow the max size("+maxBufferSize+")");
			if(size > maxBufferSize)
				throw new IllegalArgumentException("the current  size("+size+") over flow the max size("+minBufferSize+")");
		}
//		if(minBufferSize > 0 && size > minBufferSize)
//			throw new IllegalArgumentException("the current  size("+size+") over flow the min size("+minBufferSize+")");
		if(minBufferSize < size) {
			minBufferSize = size << 1;
			if(maxBufferSize > 0 && minBufferSize > maxBufferSize)
				minBufferSize = maxBufferSize;
		}
		if(size == 0)
			size = minBufferSize;
		while(size < minBufferSize) {
			size = size << 1;
			if(maxBufferSize > 0) {
				if (size > maxBufferSize || size < 0) {
					size = maxBufferSize;
				}
			}else {
				if(size < 0) {
					size = minBufferSize;
				}
			}
		}
		return size;
	}
	/**
	 * 写入数据
	 * @param message
	 * @param bufferedOverflow
	 */
	public synchronized void write(T message){
		if(message == null)
			return;
		byte[] infoHead = new byte[4];
		ByteBuffer messageBuffer = serailzation.serial(message);
		messageBuffer.flip();
		int len = messageBuffer.remaining();
		System.out.println("长度:"+len);
		infoHead = ByteUtils.intToBytes(len);
		ensureRemaining(writeBuffer, infoHead);
		while(messageBuffer.hasRemaining()) {
			if(!writeBuffer.hasRemaining())
				bufferReady.write(writeBuffer);
			writeBuffer.put(messageBuffer.get());
		}
		messageBuffer.clear();
		bufferReady.write(writeBuffer);
	}
	/**
	 * 确保数据正常写入
	 * @param byteBuffer
	 * @param bufferedOverflow
	 * @param contents
	 * @throws WriteAbortedException
	 */
	private void ensureRemaining(ByteBuffer byteBuffer, byte[] contents) {
		if(contents==null)
			return;
		int remaining =  byteBuffer.remaining();
		int len = contents.length;
		if(len > remaining) {
			int pos = 0;
			while(true) {
				bufferReady.write(byteBuffer);
				remaining =  byteBuffer.remaining();
				if(remaining <=0 && len>0)
					throw new BufferOverflowException();
				int wLen = len-pos;
				if(wLen > remaining) 
					wLen = remaining;
				if(wLen==0) 
					break;
				byteBuffer.put(contents,pos,wLen);
				pos += wLen;
			}
			return;
		}
		byteBuffer.put(contents);
	}

	public int getMaxBufferSize() {
		return maxBufferSize;
	}
	public void setMaxBufferSize(int maxBufferSize) {
		this.maxBufferSize = maxBufferSize;
	}

	@Override
	public void setBufferReady(BufferReady<T> bufferReady) {
		this.bufferReady = bufferReady;
	} 
}