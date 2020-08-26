package mhz19b;

import java.io.IOException;

import mhz19b.driver.MHZ19BDriver;
import mhz19b.util.Log;

public class App {

	private static final boolean EXTERNAL_TOOL_MODE = true;

	public void doProcess() {
		try (MHZ19BDriver mhz19b = MHZ19BDriver.getInstance("/dev/serial0")) {
		//try (MHZ19BDriver mhz19b = MHZ19BDriver.getInstance("/dev/ttyAMA0")) {
			mhz19b.open();
			mhz19b.setDetectionRange5000();
			mhz19b.setAutoCalibration(false);

			while (true) {
				int value = mhz19b.getGasConcentration();

				if (EXTERNAL_TOOL_MODE == false) {
					Log.info("co2:" + value);
				} else {
					System.out.println("co2:" + value);
				}

				Thread.sleep(10000);
			}
		} catch (InterruptedException e) {
			Log.warn(e);
		} catch (IOException e) {
			Log.warn(e);
		}
	}

    public static void main(String[] args) {
		App app 	= new App();
		app.doProcess();
    }
}

