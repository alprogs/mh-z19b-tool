package mhz19b.driver;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fazecast.jSerialComm.SerialPort;

import mhz19b.util.Log;

/*
 * Refer to https://www.winsen-sensor.com/d/files/infrared-gas-sensor/mh-z19b-co2-ver1_0.pdf
 *
 * @author s5uishida
 *
 */
public class MHZ19BDriver implements AutoCloseable {

	private static final int DEFAULT_TIMEOUT 	= 1000; // default read timeout (msec)

	private static final int BAUDRATE 	= 9600;
	private static final int DATABITS 	= 8;
	private static final int STOPBITS 	= SerialPort.ONE_STOP_BIT;
	private static final int PARITY 	= SerialPort.NO_PARITY;

	private static final byte[] CMD_GAS_CONCENTRATION 		= {(byte)0xff, 0x01, (byte)0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79};
	private static final byte[] CMD_CALIBRATE_ZERO_POINT 	= {(byte)0xff, 0x01, (byte)0x87, 0x00, 0x00, 0x00, 0x00, 0x00, 0x78};
	private static final byte[] CMD_AUTO_CALIBRATION_ON_WITHOUT_CHECKSUM 	= {(byte)0xff, 0x01, (byte)0x79, (byte)0xa0, 0x00, 0x00, 0x00, 0x00};
	private static final byte[] CMD_AUTO_CALIBRATION_OFF_WITHOUT_CHECKSUM 	= {(byte)0xff, 0x01, (byte)0x79, 0x00, 0x00, 0x00, 0x00, 0x00};

	private static final int CMD_GAS_CONCENTRATION_RET_LENGTH 	= 9;

	private static final int CALIBRATE_SPAN_POINT_MIN 	= 1000;

	private final SerialPort serialPort;
	private final String prefixPortName;
	private final String logPrefix;
	private int timeout; // read timeout (msec)

	private final AtomicInteger useCount 	= new AtomicInteger(0);

	private static final ConcurrentHashMap<String, MHZ19BDriver> map 	= new ConcurrentHashMap<String, MHZ19BDriver>();

	private static final boolean isTraceEnabled 	= false;

	synchronized public static MHZ19BDriver getInstance(String portName) {
		return getInstance(portName, DEFAULT_TIMEOUT);
	}

	synchronized public static MHZ19BDriver getInstance(String portName, int timeout) {
		MHZ19BDriver mhz19b = map.get(portName);
		if (mhz19b == null) {
			mhz19b = new MHZ19BDriver(portName, timeout);
			map.put(portName, mhz19b);
		}
		mhz19b.setTimeout(timeout);
		return mhz19b;
	}

	private void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	private MHZ19BDriver(String portName) {
		this(portName, DEFAULT_TIMEOUT);
	}

	private MHZ19BDriver(String portName, int timeout) {
		serialPort = SerialPort.getCommPort(Objects.requireNonNull(portName));
		this.timeout = timeout;
		if (portName.startsWith("/dev/")) {
			prefixPortName = "/dev/";
		} else {
			prefixPortName = "";
		}
		this.logPrefix = "[" + portName + "] ";
	}

	synchronized public void open() throws IOException {
		try {
			Log.info(logPrefix+ String.format("before - useCount:%s timeout:%d", useCount.get(), timeout));

			if (useCount.compareAndSet(0, 1)) {
				Log.info(logPrefix + "opening serial port...");
				if (!serialPort.openPort()) {
					String message = logPrefix + "failed to open serial port.";
					Log.warn(message);
					throw new IOException(message);
				}
				serialPort.setBaudRate(BAUDRATE);
				serialPort.setNumDataBits(DATABITS);
				serialPort.setNumStopBits(STOPBITS);
				serialPort.setParity(PARITY);
				serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeout, timeout);
				Log.info(logPrefix + "opened serial port.");
			}
		} finally {
			Log.info(logPrefix+ String.format("after - useCount:%s timeout:%d", useCount.get(), timeout));
		}
	}

	@Override
	synchronized public void close() throws IOException {
		try {
			Log.info(logPrefix+ String.format("before - useCount:%s timeout:%d", useCount.get(), timeout));
			if (useCount.compareAndSet(1, 0)) {
				if (serialPort.isOpen()) {
					Log.info(logPrefix + "closing serial port...");
					if (!serialPort.closePort()) {
						String message = logPrefix + "failed to close serial port.";
						Log.warn(message);
						throw new IOException(message);
					}
					Log.info(logPrefix + "closed serial port.");
				} else {
					String message = logPrefix + "already closed.";
					Log.info(message);
				}
			}
		} finally {
			Log.info(logPrefix+ String.format("after - useCount:%s timeout:%d", useCount.get(), timeout));
		}
	}

	public boolean isOpened() {
		return serialPort.isOpen();
	}

	public String getPortName() {
		return prefixPortName + serialPort.getSystemPortName();
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	private void dump(byte[] data, String tag) {
		if (isTraceEnabled) {
			StringBuffer sb = new StringBuffer();
			for (byte data1 : data) {
				sb.append(String.format("%02x ", data1));
			}
			Log.info(logPrefix+ String.format("%s%s", tag, sb.toString().trim()));
		}
	}

	private void write(byte[] out) throws IOException {
		int length = serialPort.bytesAvailable();
		if (length > 0) {
			byte[] unread = new byte[length];
			serialPort.readBytes(unread, length);
			Log.info(logPrefix + String.format("deleted unread buffer length:%s", length));
		}

		dump(out, "MH-Z19B CO2 sensor command: write: ");
		int ret = serialPort.writeBytes(out, out.length);
		if (ret == -1) {
			String message = logPrefix + "failed to write.";
			Log.warn(message);
			throw new IOException(message);
		}
	}

	private byte[] read(int size) throws IOException {
		byte[] in = new byte[size];
		int ret = serialPort.readBytes(in, size);
		if (ret == -1) {
			String message = logPrefix + "failed to read.";
			Log.warn(message);
			throw new IOException(message);
		}
		dump(in, "MH-Z19B CO2 sensor command:  read: ");
		return in;
	}

	private int convertInt(byte[] data) {

		int high 	= Integer.parseInt(Byte.toString( data[0] ), 16);
		int low 	= Integer.parseInt(Byte.toString( data[1] ), 16);
		int value 	= (high * 256) + low;

		//for (int i = 0; i < data.length; i++) {
		//	value = (value << 8) + (data[i] & 0xff);
		//}
		return value;
	}

	private byte getCheckSum(byte[] data) {
		int ret = 0;
		for (int i = 1; i <= 7; i++) {
			ret += (int)data[i];
		}
		return (byte)(~(byte)(ret & 0x000000ff) + 1);
	}

	private byte[] getCommandWithCheckSum(byte[] baseCommand) {
		byte[] checkSum = {getCheckSum(baseCommand)};
		byte[] data = new byte[baseCommand.length + 1];
		System.arraycopy(baseCommand, 0, data, 0, baseCommand.length);
		System.arraycopy(checkSum, 0, data, baseCommand.length, 1);
		return data;
	}

	public int getGasConcentration() throws IOException {
		write(CMD_GAS_CONCENTRATION);
		byte[] received = read(CMD_GAS_CONCENTRATION_RET_LENGTH);

		byte[] data = {received[2], received[3]};
		int value = convertInt(data);

		return value;
	}

	public void setCalibrateZeroPoint() throws IOException {
		write(CMD_CALIBRATE_ZERO_POINT);
		Log.info(logPrefix+ "set the calibration zero point to 400 ppm.");

	}

	public void setCalibrateSpanPoint(int point) throws IOException {
		if (point < CALIBRATE_SPAN_POINT_MIN) {
			Log.info(logPrefix+ String.format("since span needs at least %d ppm, set it to %d ppm.", CALIBRATE_SPAN_POINT_MIN, CALIBRATE_SPAN_POINT_MIN));
			point = CALIBRATE_SPAN_POINT_MIN;
		}

		byte high = (byte)((point / 256) & 0x000000ff);
		byte low = (byte)((point % 256) & 0x000000ff);
		byte[] CMD_CALIBRATE_SPAN_POINT = {(byte)0xff, 0x01, (byte)0x88, high, low, 0x00, 0x00, 0x00};

		write(getCommandWithCheckSum(CMD_CALIBRATE_SPAN_POINT));
		Log.info(logPrefix+ String.format("set the calibration span point to %d ppm.", point));
	}

	public void setAutoCalibration(boolean set) throws IOException {
		if (set) {
			write(getCommandWithCheckSum(CMD_AUTO_CALIBRATION_ON_WITHOUT_CHECKSUM));
			Log.info(logPrefix + "set auto calibration to ON.");
		} else {
			write(getCommandWithCheckSum(CMD_AUTO_CALIBRATION_OFF_WITHOUT_CHECKSUM));
			Log.info(logPrefix + "set auto calibration to OFF.");
		}
	}

	private void setDetectionRange(int range) throws IOException {
		byte high = (byte)((range / 256) & 0x000000ff);
		byte low = (byte)((range % 256) & 0x000000ff);
		byte[] CMD_DETECTION_RANGE = {(byte)0xff, 0x01, (byte)0x99, high, low, 0x00, 0x00, 0x00};

		write(getCommandWithCheckSum(CMD_DETECTION_RANGE));
		Log.info(logPrefix+ String.format("set the detection range to %d ppm.", range));
	}

	public void setDetectionRange2000() throws IOException {
		setDetectionRange(2000);
	}

	public void setDetectionRange5000() throws IOException {
		setDetectionRange(5000);
	}

	/******************************************************************************************************************
	 * Sample main
	 ******************************************************************************************************************/
	public static void main(String[] args) throws IOException {
		MHZ19BDriver mhz19b = null;
		try {
			mhz19b = MHZ19BDriver.getInstance("/dev/ttyAMA0");
			mhz19b.open();
			mhz19b.setDetectionRange5000();
			mhz19b.setAutoCalibration(false);

			while (true) {
				int value = mhz19b.getGasConcentration();
				Log.info("co2:" + value);

				Thread.sleep(10000);
			}
		} catch (InterruptedException e) {
			Log.warn(e);
		} catch (IOException e) {
			Log.warn(e);
		} finally {
			if (mhz19b != null) {
				mhz19b.close();
			}
		}
	}
}
