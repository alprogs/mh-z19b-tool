package mhz19b;

import java.io.IOException;

import mhz19b.driver.MHZ19BDriver;
import mhz19b.util.Log;

public class App {

	public void doProcess() {
		try (MHZ19BDriver mhz19b = MHZ19BDriver.getInstance("/dev/serial0")) {
		//try (MHZ19BDriver mhz19b = MHZ19BDriver.getInstance("/dev/ttyAMA0")) {
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
		}
	}

    public static void main(String[] args) {
		App app 	= new App();
		app.doProcess();
    }
}

