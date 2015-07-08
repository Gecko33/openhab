/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmr100.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codeminders.hidapi.HIDDevice;

/**
 * <p>This class is derived from the excellent work made by 
 * Kenneth J. Turner (http://www.cs.stir.ac.uk/~kjt).</p>
 * 
 * <p>This code results from some refactoring and adaptation to 
 * the needs of the WMR100 Binding.</p>
 * 
 * <p>Any information about the original code is available here: 
 * http://www.cs.stir.ac.uk/~kjt/software/comms/wxlogger.html</p>
 * 
 * @author Kenneth J. Turner (http://www.cs.stir.ac.uk/~kjt)
 * @version 1.0 (18th February 2013)
 */
public class WxLogger implements WMRConstants {
	
	private static boolean active;
	
	private static final Logger logger = LoggerFactory.getLogger(WxLogger.class);
	
	public static Map<String,Object> DATA = new HashMap<String, Object>();

	

	// ------------------------ customisation constants
	// --------------------------

	/** Degree character */
	private static final String DEGREE = "\u00B0"; // with Unicode console
	// ""; // without Unicode console

	/** Default log flags (logical OR of individual log flags) */
	private static final int LOG_DEFAULT = LOG_HOUR;

	/** Logging interval (minutes, must be sub-multiple of 60) */
	private static final int LOG_INTERVAL = 15;

	/** Channel for outdoor sensor (value 1/2/4? = channel 1/2/3) */
	private static final int OUTDOOR_SENSOR = 1;

	// -------------------------- measurement constants
	// --------------------------

	/** Number of measurement types */
	private static final int MEASURE_SIZE = 12;

	/** Number of periods per hour */
	private static final int PERIOD_SIZE = 60 / LOG_INTERVAL;

	/** Low battery status symbol */
	private static final char STATUS_BATTERY = '!';

	/** Missing data status symbol */
	private static final char STATUS_MISSING = '?';

	/** Sensor status OK symbol */
	private static final char STATUS_OK = ' ';

	// ------------------------------ USB constants
	// ------------------------------

	/** Anemometer code */
	private final static byte CODE_ANEMOMETER = (byte) 0x48;

	/** Barometer code */
	private final static byte CODE_BAROMETER = (byte) 0x46;

	/** Clock code */
	private final static byte CODE_CLOCK = (byte) 0x60;

	/** Rainfall bucket code */
	private final static byte CODE_RAINFALL = (byte) 0x41;

	/** Thermohygrometer code */
	private final static byte CODE_THERMOHYGROMETER = (byte) 0x42;

	/** UV code */
	private final static byte CODE_UV = (byte) 0x47;

	/** Size of buffer for USB responses (bytes) */
	private static final int RESP0NSE_SIZE = 9;

	/** Timeout for reading USB data (sec) */
	private static final int RESPONSE_TIMEOUT = 10;

	/** Weather station initialisation command */
	private static final byte[] STATION_INITIALISATION = { (byte) 0x00,
			(byte) 0x20, (byte) 0x00, (byte) 0x08, (byte) 0x01, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00 };

	/** Weather station USB product identifier */
	private static final int STATION_PRODUCT = 0xCA01;

	/** Weather station data request command */
	private static final byte[] STATION_REQUEST = { (byte) 0x00, (byte) 0x01,
			(byte) 0xD0, (byte) 0x08, (byte) 0x01, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00 };

	/**
	 * Time from last sensor data or data request before requesting data again
	 * (sec); this should be more than 60 seconds (the normal response interval
	 * of a WMR100)
	 */
	private static final int STATION_TIMEOUT = 90;

	/** Size of buffer for accumulated USB data (bytes) */
	private static final int STATION_SIZE = 100;

	/** Weather station USB vendor identifier */
	private static final int STATION_VENDOR = 0x0FDE;

	/** Logical OR of individual flags */
	private static int logFlags = LOG_FRAME | LOG_HOUR | LOG_SENSOR | LOG_USB;

	/** Number of consecutive full stop characters from console */
	private static int stopCount;

	/** Clock day (0..31) */
	private static int clockDay;

	/** Clock hour (0..23) */
	private static int clockHour;

	/** Clock minute (0..59) */
	private static int clockMinute;

	/** Clock day (1..12) */
	private static int clockMonth;

	/** Clock day (1..12) */
	private static int clockYear;

	/** Timer that signals every minute */
	private static Timer minuteTimer = new Timer();

	/** Count of measurement values (indexes measurement, period) */
	private static int[][] measureCount = new int[MEASURE_SIZE][PERIOD_SIZE];

	/** Print format strings for reporting measurements */
	private static String[] measureFormat = new String[MEASURE_SIZE];

	/** Minimum measurement values (indexes measurement, period) */
	private static float[][] measureMin = new float[MEASURE_SIZE][PERIOD_SIZE];

	/** Maximum measurement values (indexes measurement, period) */
	private static float[][] measureMax = new float[MEASURE_SIZE][PERIOD_SIZE];

	/** Total measurement values (indexes measurement, period) */
	private static float[][] measureTotal = new float[MEASURE_SIZE][PERIOD_SIZE];

	/** Rainfall total for midnight */
	private static float outdoorTemperature;

	/** Current period index in hour */
	private static int period;

	/** Lowest period index (normally 0, different for first hour of running) */
	private static int periodLow;

	/** Initial rainfall offset (normally rain since midnight in mm) */
	private static float rainInitial;

	/** Anemometer status */
	private static char statusAnemometer;

	/** Barometer status */
	private static char statusBarometer;

	/** Rain gauge status */
	private static char statusRain;

	/** Outdoor thermohygrometer status */
	private static char statusThermohygrometer;

	/** UV sensor status */
	private static char statusUV;

	// ----------------------------- USB variables
	// ------------------------------

	/** Human Interface Device instance */
	private static HIDDevice hidDevice;

	/** Last time sensor data was received or data was requested (msec) */
	private static long lastTime;

	/** USB response buffer */
	private static byte[] responseBuffer = new byte[RESP0NSE_SIZE];

	/** USB response length for each sensor code */
	private static HashMap<Byte, Integer> responseLength = new HashMap<Byte, Integer>();

	/** USB response byte count */
	private static int responseBytes;

	/** Accumulated USB data buffer */
	private static byte[] stationBuffer = new byte[STATION_SIZE];

	/** Next index to be used in accumulated USB data buffer */
	private static int stationNext;

	// ******************************* Main Program
	// ******************************


	// ********************************* Methods
	// *********************************

	/**
	 * Add a measurement (identified by its index) and value for the current
	 * period. Count, minimum, maximum and total are maintained for each
	 * measurement.
	 * 
	 * @param measure
	 *            measure index
	 * @param value
	 *            measure value
	 */
	private static void addMeasure(int measure, float value) { 
		if (0 <= measure && measure < MEASURE_SIZE && 0 <= period && period < PERIOD_SIZE) { // measure in range and period in range?
			int count = measureCount[measure][period];// get measurement count
			if (count == 0) { // no previous measurements?
				measureMin[measure][period] = value; // initialise minimum
				measureMax[measure][period] = value; // initialise maximum
				measureTotal[measure][period] = value; // initialise total
			} else { // previous measurements
				if (value < measureMin[measure][period]) {// new minimum?
					measureMin[measure][period] = value; // set minimum
				}
				if (value > measureMax[measure][period]) {// new maximum?
					measureMax[measure][period] = value; // set maximum
				}
				measureTotal[measure][period] += value; // add value
			}
			measureCount[measure][period] = count + 1;// increment measurement count
		} else {
			// measure/period out of range
			logError(String.format("Measure %s and period %s must be in range", measure, period));
		}
	}

	/**
	 * Analyse and store anemometer data (wind direction in degrees, wind speed
	 * average in m/s, wind chill temperature in Celsius). If the base unit
	 * reports a zero (i.e. not applicable) value for wind chill, the outside
	 * temperature (if known) is used instead.
	 * 
	 * @param frame
	 *            sensor data
	 */
	private static void analyseAnemometer(byte[] frame) {
		String batteryDescription = getBattery(getInt(frame[0]) / 64); // get battery level description
		
		statusAnemometer = setBatteryStatus(CODE_ANEMOMETER, batteryDescription); // set anemometer battery status
		
		float windGust = (256.0f * (getInt(frame[5]) % 16) + getInt(frame[4])) / 10.0f;// get wind speed gust (m/s)
		
		float windAverage = (16.0f * getInt(frame[6]) + getInt(frame[5]) / 16) / 10.0f;// get wind speed average (m/s)
		
		int windDirection = frame[2] % 16; // get wind direction (16ths)
		String directionDescription = getDirection(windDirection);// get wind direction descr.
		
		windDirection = Math.round((frame[2] % 16) * 22.5f);// get wind direction (deg)
		
		int chillSign = getInt(frame[8]) / 16; // get wind chill sign quartet
		boolean chillValid = (chillSign & 0x2) == 0;// get wind chill validity
		chillSign = getSign(chillSign / 8); // get wind chill sign
		float windChill = chillSign * getInt(frame[7]); // get wind chill (deg C)
				
		String chillDescription = chillValid ? Float.toString(windChill) + DEGREE : "N/A";// set wind chill description
		
		if ((logFlags & LOG_SENSOR) != 0) {// sensor data to be output?
			logInfo(String.format("Anemometer: Direction %s %s (%s), Average %s m/s, Gust %s m/s, Chill %s, Battery %s", 
					windDirection, DEGREE, directionDescription, windAverage, windGust, chillDescription, batteryDescription));
		}
		if (outdoorTemperature != DUMMY_VALUE) { // outdoor temperature known?
			if (windGust > 1.3) { // wind speed high enough?
				float windPower = (float) Math.pow(windGust, 0.16);// get (gust speed)^0.16
				windChill = 13.12f + (0.6215f * outdoorTemperature) - (13.956f * windPower) + (0.487f * outdoorTemperature * windPower);// calculate wind chill (deg C)
				
				if (windChill > outdoorTemperature) {// invalid chill calculation?
					windChill = outdoorTemperature; // reset chill to outdoor temp.
				}
			} else {
				// wind speed not high enough
				windChill = outdoorTemperature; // set chill to outdoor temp.
			}
		} else if (!chillValid) {// no outdoor temp. or chill?
			windChill = 0.0f; // set dummy chill of 0 (deg C)
		}
		addMeasure(INDEX_WIND_DIRECTION, windDirection); // add wind dir measurement
		addMeasure(INDEX_WIND_SPEED, windAverage); // add wind speed measurement
		addMeasure(INDEX_WIND_CHILL, windChill); // add wind chill measurement
		
		// JCO: we'll consider sensor is 0, since no sensor ID is sent in this frame.
		// Nevertheless, only one wind sensor seems to be connectable.
		DATA.put("windDirection:0", windDirection);
		DATA.put("windGust:0", windGust);
		DATA.put("windAverage:0", windAverage);
		DATA.put("windChill:0", windChill);
	}

	/**
	 * Analyse and store barometer data (absolute pressure in mb).
	 * 
	 * @param frame
	 *            sensor data
	 */
	private static void analyseBarometer(byte[] frame) {
		int pressureAbsolute = 256 * (getInt(frame[3]) % 16) + getInt(frame[2]);// get absolute pressure (mb)
		int pressureRelative = 256 * (getInt(frame[5]) % 16) + getInt(frame[4]);// get relative pressure (mb)
		String weatherForecast = getWeather(getInt(frame[3]) / 16);// get forecast weather descrip.
		String weatherPrevious = getWeather(getInt(frame[5]) / 16);// get previous weather descrip.
		
		if ((logFlags & LOG_SENSOR) != 0) {// sensor data to be output?
			logInfo(String.format("Barometer: Pressure (Abs.) %s mb, Pressure (Rel.) %s mb, Forecast %s, Previous %s.", 
					pressureAbsolute, pressureRelative, weatherForecast, weatherPrevious));
		}
				
		addMeasure(INDEX_INDOOR_PRESSURE, pressureAbsolute);// add absolute pressure
		
		// JCO - always Sensor 0 for those data.
		DATA.put("pressureAbsolute:0", pressureAbsolute); // in mb
		DATA.put("pressureRelative:0", pressureRelative); // in mb
		DATA.put("weatherForecast:0", weatherForecast); // text
		DATA.put("weatherPrevious:0", weatherPrevious); // text
	}

	/**
	 * Analyse clock data, but do not store this as it can be unreliable due to
	 * irregular arrival or the checksum not detecting an error.
	 * 
	 * @param frame
	 *            sensor data
	 * @throws input
	 *             -output exception
	 */
	private static void analyseClock(byte[] frame) throws IOException {
		int minute = getInt(frame[4]) % 60; // get minute, limited to 59
		int hour = getInt(frame[5]) % 24; // get hour, limited to 23
		int day = getInt(frame[6]) % 32; // get day, limited to 31
		int month = getInt(frame[7]) % 13; // get month, limited to 12
		int year = CENTURY + (getInt(frame[8]) % 100); // get year, limited to
														// 99
		int zoneSign = // get time zone sign
		getSign(getInt(frame[9]) / 128);
		int zone = getInt(frame[9]) % 128; // get time zone
		int radioLevel = (getInt(frame[0]) / 16) % 4; // get radio level
		String radioDescription = getRadio(radioLevel); // get radio description
		String time = getTime(hour, minute); // get current time
		String date = getDate(year, month, day); // get current date
		if ((logFlags & LOG_SENSOR) != 0) {// sensor data to be output?
			logInfo(String.format("Clock: Time %s, Date %s, UTC %sh, Radio %s (%s).", 
					time, date, String.format("%+2d", zoneSign*zone), radioLevel, radioDescription));
		}
	}

	/**
	 * Analyse and store sensor data according to the weather sensor.
	 * 
	 * @param frame
	 *            sensor data
	 * @throws input
	 *             -output exception
	 */
	private static void analyseFrame(byte[] frame) throws IOException {
		if ((logFlags & LOG_FRAME) != 0) // frame output needed?
			logBuffer("Frame", frame.length, frame); // output frame buffer
		if (validFrame(frame)) { // frame checksum valid?
			logInfo("!!Valid frame!!");
			long actualTime = currentTime(); // get current time in msec
			byte sensorCode = frame[1]; // get sensor code
			switch (sensorCode) { // check sensor code
			case CODE_ANEMOMETER: // anemometer?
				lastTime = actualTime; // note time of sensor data
				analyseAnemometer(frame); // analyse anemometer data
				break;
			case CODE_BAROMETER: // barometer?
				lastTime = actualTime; // note time of sensor data
				analyseBarometer(frame); // analyse barometer data
				break;
			case CODE_CLOCK: // clock?
				analyseClock(frame); // analyse clock data
				break;
			case CODE_RAINFALL: // rain gauge?
				lastTime = actualTime; // note time of sensor data
				analyseRainfall(frame); // analyse rainfall data
				break;
			case CODE_THERMOHYGROMETER: // thermohygrometer?
				lastTime = actualTime; // note time of sensor data
				analyseThermohygrometer(frame); // analyse thermohygrometer data
				break;
			case CODE_UV: // UV sensor?
				lastTime = actualTime; // note time of sensor data
				analyseUV(frame); // analyse UV data
				break;
			default: // unrecognised sensor
				logError("Ignoring unknown sensor code 0x"
						+ String.format("%02X", sensorCode));
			}
			// JCO now let's notify listeners, then clear data map.
			pushData();
			WxLogger.DATA.clear();
		}
		else // frame checksum invalid
			logInfo("Invalid frame checksum");
	}

	/**
	 * /** Analyse and store rainfall data (total rainfall since midnight in
	 * mm).
	 * 
	 * @param frame
	 *            sensor data
	 */
	private static void analyseRainfall(byte[] frame) {
		String batteryDescription = getBattery(getInt(frame[0]) / 64);// get battery level description
		
		statusRain = setBatteryStatus(CODE_RAINFALL, batteryDescription); // set rain gauge battery status
		
		float rainRate = getRain(256.0f * getInt(frame[3]) + getInt(frame[2]));// get rainfall rate (mm/hr)
		
		float rainRecent = getRain(256.0f * getInt(frame[5]) + getInt(frame[4])); // get recent (mm)
		
		float rainDay = getRain(256.0f * getInt(frame[7]) + getInt(frame[6]));// get rainfall for day (mm)
		
		float rainReset = getRain(256.0f * getInt(frame[9]) + getInt(frame[8])); // get rainfall since reset (mm)
		
		if (rainInitial == DUMMY_VALUE) {// initial rain offset unknown?
			rainInitial = rainReset; // use rain since last reset
		} else if (rainReset < rainInitial) {// rain memory has been reset?
			rainInitial = -rainInitial; // adjust initial rain offset
		}
		float rainMidnight = Math.round(10.0f * (rainReset - rainInitial)) / 10.0f;// set rain total since midnight to 1 dec.
																// place
		int minute = getInt(frame[10]) % 60; // get minute, limited to 59
		int hour = getInt(frame[11]) % 24; // get hour, limited to 23
		int day = getInt(frame[12]) % 32; // get day, limited to 31
		int month = getInt(frame[13]) % 13; // get month, limited to 12
		int year = CENTURY + (getInt(frame[14]) % 100); // get year, limited to
														// 99
		String resetTime = getTime(hour, minute); // get last reset time
		String resetDate = getDate(year, month, day); // get last reset date
		if ((logFlags & LOG_SENSOR) != 0) {// sensor data to be output?
			logInfo(String.format("Rain Gauge: Rate %s mm/h, Recent %s mm, 24 Hour %s mm, From Midnight %s mm, From Reset %s mm, Reset %s %s, Battery %s", 
					rainRate, rainRecent, rainDay, rainMidnight, rainReset, resetTime, resetDate, batteryDescription));
		}
		addMeasure(INDEX_RAIN_TOTAL, rainMidnight); // add daily rain measurement
	}

	/**
	 * Analyse USB response for frames, processing then removing these.
	 * 
	 * @param bytes
	 *            number of bytes to log
	 * @param buffer
	 *            data buffer to log
	 * @throws input
	 *             -output exception
	 */
	private static void analyseResponse(int bytes, byte[] buffer) throws IOException {
		int i, j; // buffer positions
		int count = buffer[0]; // get buffer count
		if (bytes > 0 & count > 0) { // response data to check?
			if ((logFlags & LOG_USB) != 0) {// USB logging?
				logBuffer("USB", bytes, buffer); // output USB response
			}
			if (count < buffer.length && stationNext + count <= stationBuffer.length) {// will not overflow buffers?
					
				for (i = 0; i < count; i++) {
					// go through response bytes
					stationBuffer[stationNext++] = buffer[i + 1];// copy USB to station buffer
				}
				int startDelimiter = getDelimiter(0); // check for frame start
				if (startDelimiter != -1) { // frame start found?
					startDelimiter += 2; // move past frame start
					int finishDelimiter = getDelimiter(startDelimiter); // check for frame finish
					
					if (finishDelimiter != -1) { // frame finish found?
						if (startDelimiter < finishDelimiter) { // non-empty frame?
							byte[] frameBuffer = Arrays.copyOfRange(stationBuffer, startDelimiter, finishDelimiter); // copy station data to frame
							analyseFrame(frameBuffer); // analyse frame
							i = 0; // initialise "to" index
							for (j = finishDelimiter; j < stationNext; j++) {
								// go through data
								stationBuffer[i++] = stationBuffer[j];// copy following data down
							}
							stationNext = i; // set new station data start
						} else {
							// empty frame
							logError("Empty frame received"); // report error
						}
					}
				}
			} else {
				// will overflow buffers
				// report error
				logError(String.format("Over-long response received - count %s, buffer index %s", count, stationNext)); 
			}
		}
	}

	/**
	 * Analyse and store thermohygrometer data (indoor/outdoor temperature in
	 * Celsius, indoor/outdoor relative humidity in %, indoor/outdoor dewpoint
	 * in Celsius). If the sensor is not the outdoor one designed for recording,
	 * nothing is logged.
	 * 
	 * @param frame
	 *            sensor data
	 */
	private static void analyseThermohygrometer(byte[] frame) {
		String batteryDescription = // get battery level description
		getBattery(getInt(frame[0]) / 64);
		int sensor = getInt(frame[2]) % 16; // get sensor number
		int temperatureSign = getSign(getInt(frame[4]) / 16);// get temperature sign
		float temperature = temperatureSign * (256.0f * (getInt(frame[4]) % 16) + getInt(frame[3])) / 10.0f;// get temperature (deg C)
		String temperatureTrend = getTrend((getInt(frame[0]) / 16) % 4);// get temperature trend
		int humidity = getInt(frame[5]) % 100; // get humidity (%)
		String humidityTrend = getTrend((getInt(frame[2]) / 16) % 4);// get humidity trend
		int dewpointSign = getSign(getInt(frame[7]) / 16);// get dewpoint sign
		float dewpoint = dewpointSign * (256.0f * (getInt(frame[7]) % 16) + getInt(frame[6])) / 10.0f;// get dewpoint (deg C)
		boolean heatValid = (getInt(frame[9]) & 0x20) == 0;// get heat index validity
		float heatIndex = dewpointSign * fahrenheitCelsius((256.0f * (getInt(frame[9]) % 8) + getInt(frame[8])) / 10.0f);// get heat index (deg C)
		String heatDescription = heatValid ? heatIndex + DEGREE + "C" : "N/A";// get heat index description
		
		if ((logFlags & LOG_SENSOR) != 0) { // sensor data to be output?
			logInfo(String.format("Thermohygrometer: Sensor %s, Temperature %s %sC (%s), Humidity %s %% (%s), Dewpoint %s %s C, Index %s, Battery", 
					sensor, temperature, DEGREE, temperatureTrend, humidity, humidityTrend, dewpoint, heatDescription, batteryDescription));
		}
		if (sensor == 0) { // indoor sensor?
			addMeasure(INDEX_INDOOR_TEMPERATURE, temperature);// add indoor temperature
			addMeasure(INDEX_INDOOR_HUMIDITY, humidity);// add indoor humidity
			addMeasure(INDEX_INDOOR_DEWPOINT, dewpoint);// add indoor dewpoint
		} else if (sensor == OUTDOOR_SENSOR) { // outdoor sensor?
			statusThermohygrometer = setBatteryStatus(CODE_THERMOHYGROMETER, batteryDescription);// set thermo. battery status
			outdoorTemperature = temperature; // set outdoor temperature
			addMeasure(INDEX_OUTDOOR_TEMPERATURE, temperature);// add outdoor temperature
			addMeasure(INDEX_OUTDOOR_HUMIDITY, humidity);// add outdoor humidity
			addMeasure(INDEX_OUTDOOR_DEWPOINT, dewpoint);// add outdoor dewpoint
		}
		// JCO
		DATA.put("temperature:" + sensor, temperature);
		DATA.put("humidity:" + sensor, humidity);
		DATA.put("dewpoint:" + sensor, dewpoint);
	}

	/**
	 * Analyse and store ultraviolet data (UV index from 0 upwards).
	 * 
	 * @param frame
	 *            sensor data
	 */
	private static void analyseUV(byte[] frame) {
		String batteryDescription = getBattery(getInt(frame[0]) / 64); // get battery level description
		
		statusUV = setBatteryStatus(CODE_UV, batteryDescription);// set UV sensor battery status
		
		int uvIndex = getInt(frame[3]) & 0xF; // get UV index
		String uvDescription = getUV(uvIndex); // get UV index description
		if ((logFlags & LOG_SENSOR) != 0) {// sensor data to be output?
			logInfo(String.format("UV: Index %s (%s), Battery %s", uvIndex, uvDescription, batteryDescription));
		}
		addMeasure(INDEX_UV_INDEX, uvIndex); // add UV index measurement
	}

	/**
	 * Check things on the minute. The current period is adjusted according to
	 * the minute of the hour. Normally the hour advances by 1 (modulo 24), so
	 * data for the hour just past is logged/reported. All measures are then
	 * re-initialised. Summertime is presumed to involve only a 1 hour
	 * adjustment and not to cross midnight (e.g. not 00:00 to 23:00). When
	 * summertime begins, data for the hour just past is logged/reported twice
	 * (for a fictitious extra hour and the actual hour). This may result in the
	 * rainfall total being incorrect if it changed during the hour. When
	 * summertime ends, data for the hour just past continues to be accumulated
	 * and will appear in the next hourly log.
	 * 
	 * @throws input
	 *             -output exception
	 */
	private static void checkMinute() throws IOException {
		Calendar now = Calendar.getInstance(); // get current date and time
		int hour = now.get(Calendar.HOUR_OF_DAY); // get current hour
		int minute = now.get(Calendar.MINUTE); // get current minute
		period = minute / LOG_INTERVAL; // update period number
		if (minute == 0) { // on the hour?
			int hourDifference = hour - clockHour; // get hour difference
			if (hourDifference < 0) // hour moved back?
				hourDifference += 24; // get positive difference
			if (hourDifference == 1) { // hour forward 1?
				logHour(hour); // log hour
				initialiseMeasures(); // initialise measurement values
			} else if (hourDifference == 2) { // hour forward 2?
				logHour(hour - 1); // log first hour
				logHour(hour); // log second hour
				initialiseMeasures(); // initialise measurement values
			}
		}
	}

	/**
	 * Return current time in msec
	 * 
	 * @return current time in msec
	 */
	private static long currentTime() {
		return (System.nanoTime() / 1000000); // return current time in msec
	}

	/**
	 * Return start position of frame delimiter (i.e. two byes of 0xFF).
	 * 
	 * @return start position of frame delimiter (-1 if not found)
	 */
	private static int getDelimiter(int pos) {
		for (int i = pos; i < stationNext - 2; i++) {
			if (stationBuffer[i] == FRAME_BYTE && stationBuffer[i + 1] == FRAME_BYTE) {
				return (i);
			}
		}
		return (-1);
	}

	/**
	 * Return Celsius equivalent of Fahrenheit temperature.
	 * 
	 * @param fahrenheit
	 *            Fahrenheit temperature
	 * @return Celsius temperature
	 */
	private static float fahrenheitCelsius(float fahrenheit) {
		return (0.5555f * (fahrenheit - 32.0f)); // return Celsius equivalent
	}

	/**
	 * Return description corresponding to battery code.
	 * 
	 * @param batteryCode
	 *            battery code
	 * @return battery description
	 */
	private static String getBattery(int batteryCode) {
		int batteryIndex = batteryCode == 0 ? 0 : 1;// get battery description index
		return (BATTERY_DESCRIPTION[batteryIndex]); // return battery description
	}

	/**
	 * Extract current hour and minute into global clock variables.
	 * 
	 * @return current calendar
	 */
	private static Calendar getClockTime() {
		Calendar now = Calendar.getInstance(); // get current date and time
		clockHour = now.get(Calendar.HOUR_OF_DAY); // get current hour
		clockMinute = now.get(Calendar.MINUTE); // get current minute
		return (now); // return current calendar
	}

	/**
	 * Return formatted date.
	 * 
	 * @param year
	 *            year
	 * @param month
	 *            month
	 * @param day
	 *            day
	 * @return DD/MM/YYYY
	 */
	private static String getDate(int year, int month, int day) {
		return (String.format("%02d/%02d/%04d", day, month, year));  // return DD/MM/YYYY
	}

	/**
	 * Return description corresponding to wind direction code.
	 * 
	 * @param directionCode
	 *            wind direction code
	 * @return wind direction description
	 */
	private static String getDirection(int directionCode) {
		return (directionCode < DIRECTION_DESCRIPTION.length ? DIRECTION_DESCRIPTION[directionCode] : "Unknown");// return wind direction descr. // weather dir. in range?
	}

	/**
	 * Return integer value of byte.
	 * 
	 * @param value
	 *            byte value
	 * @return integer value
	 */
	private static int getInt(byte value) {
		return ((int) value & 0xFF); // return bottom 8 bits
	}

	/**
	 * Return description corresponding to radio code.
	 * 
	 * @param radioCode
	 *            radio code
	 * @return radio description
	 */
	private static String getRadio(int radioCode) {
		return (radioCode < RADIO_DESCRIPTION.length ? RADIO_DESCRIPTION[radioCode] : "Strong");// return radio description // radio code in range?
	}

	/**
	 * Return rainfall in inches.
	 * 
	 * @param rain
	 *            rainfall in 100ths of inches
	 * @return rainfall in mm
	 */
	private static float getRain(float rain) {
		rain = rain / 100.0f * 25.39f; // get rainfall in mm
		rain = Math.round(rain * 10.0f) / 10.0f; // round to one decimal place
		return (rain); // return rainfall in mm
	}

	/**
	 * Return sign value corresponding to sign code (0 positive, non-0
	 * negative).
	 * 
	 * @param signCode
	 *            sign code
	 * @return sign (+1 or -1)
	 */
	private static int getSign(int signCode) {
		return (signCode == 0 ? +1 : -1); // return sign code
	}

	/**
	 * Return formatted time.
	 * 
	 * @param hour
	 *            hour
	 * @param minute
	 *            minute
	 * @return HH:MM
	 */
	private static String getTime(int hour, int minute) {
		return (String.format("%02d:%02d", hour, minute)); // return HH:MM
	}

	/**
	 * Return description corresponding to trend code.
	 * 
	 * @param trendCode
	 *            trend code
	 * @return trend description
	 */
	private static String getTrend(int trendCode) {
		return (trendCode < TREND_DESCRIPTION.length ? TREND_DESCRIPTION[trendCode] : "Unknown"); // return trend description// trend code in range?
	}

	/**
	 * Return description corresponding to UV code.
	 * 
	 * @param uvCode
	 *            uvCode code
	 * @return uvCode description
	 */
	private static String getUV(int uvCode) {
		int uvIndex = (uvCode >= 11 ? 4 : uvCode >= 8 ? 3 : uvCode >= 6 ? 2 : uvCode >= 3 ? 1 : 0);
		return (UV_DESCRIPTION[uvIndex]); // get UV description index // return UV description
	}

	/**
	 * Return description corresponding to weather code.
	 * 
	 * @param weatherCode
	 *            weather code
	 * @return weather description
	 */
	private static String getWeather(int weatherCode) {
		return (weatherCode < WEATHER_DESCRIPTION.length ? WEATHER_DESCRIPTION[weatherCode]	: "Unknown"); // return weather description // weather code in range?
	}

	/**
	 * Initialise program variables.
	 */
	public static void initialise() {
		stopCount = 0; // set no full stops yet

		Calendar now = Calendar.getInstance(); // get current date and time
		clockHour = now.get(Calendar.HOUR_OF_DAY); // set current hour
		clockMinute = now.get(Calendar.MINUTE); // set current minute
		clockDay = now.get(Calendar.DAY_OF_MONTH); // set current day
		clockMonth = now.get(Calendar.MONTH) + 1; // set current month
		clockYear = now.get(Calendar.YEAR); // get current year
		long firstDelay = now.getTimeInMillis(); // get current time in msec
		now.roll(Calendar.MINUTE, true); // go to next minute
		now.set(Calendar.SECOND, SECOND_OFFSET); // set offset seconds
		now.set(Calendar.MILLISECOND, 0); // set zero msec
		firstDelay = now.getTimeInMillis() - firstDelay; // get msec to next
															// minute
		if (firstDelay < 0) {// already at next minute?
			firstDelay = 0; // set no delay
		}
		TimerTask minuteTask = new TimerTask() { // create minute timer task
			public void run() { // define task execution
				try { // try to check minutes
					checkMinute(); // analyse minute
				} catch (IOException exception) { // catch I/O exception
					logError( // output log error
					"Could not perform minute check: " + exception);
				}
			}
		};
		minuteTimer.scheduleAtFixedRate(minuteTask, firstDelay, 60000); // schedule minute checks // set task, delay, 1 minute
				 

		responseLength.put(CODE_ANEMOMETER, 11); // set anemometer frame length
		responseLength.put(CODE_BAROMETER, 8); // set barometer frame length
		responseLength.put(CODE_CLOCK, 12); // set clock frame length
		responseLength.put(CODE_RAINFALL, 17); // set rainfall frame length
		responseLength.put(CODE_THERMOHYGROMETER, 12); // set thermo. frame length
		responseLength.put(CODE_UV, 6); // set UV frame length
		measureFormat[INDEX_WIND_SPEED] = "%5.1f"; // wind speed format
		measureFormat[INDEX_WIND_DIRECTION] = "%4.0f"; // wind direction format
		measureFormat[INDEX_OUTDOOR_TEMPERATURE] = "%6.1f"; // outdoor temp. format
		measureFormat[INDEX_OUTDOOR_HUMIDITY] = "%3.0f"; // outdoor humidity format
		measureFormat[INDEX_OUTDOOR_DEWPOINT] = "%6.1f"; // outdoor dewpoint format
		measureFormat[INDEX_INDOOR_PRESSURE] = "%7.1f"; // indoor pressure format
		measureFormat[INDEX_INDOOR_TEMPERATURE] = "%6.1f"; // indoor temp. format
		measureFormat[INDEX_INDOOR_HUMIDITY] = "%3.0f"; // indoor humidity format
		measureFormat[INDEX_RAIN_TOTAL] = "%4.0f"; // rainfall total format
		measureFormat[INDEX_WIND_CHILL] = "%6.1f"; // outdoor wind chill format
		measureFormat[INDEX_INDOOR_DEWPOINT] = "%6.1f"; // indoor dewpoint format
		measureFormat[INDEX_UV_INDEX] = "%3.0f"; // outdoor UV index format
		lastTime = 0; // set no time for last data
		outdoorTemperature = DUMMY_VALUE; // set outdoor temp. unknown
		rainInitial = DUMMY_VALUE; // set no midnight rain offset
		stationNext = 0; // set no station data yet
		initialiseMeasures(); // initialise measures
	}

	/**
	 * Initialise measurement and period variables.
	 */
	private static void initialiseMeasures() {
		statusAnemometer = STATUS_OK; // assume anemometer status OK
		statusBarometer = STATUS_OK; // assume barometer status OK
		statusRain = STATUS_OK; // assume rain gauge status OK
		statusThermohygrometer = STATUS_OK; // assume thermo. status OK
		statusUV = STATUS_OK; // assume UV sensor status OK
		if (clockHour == 0) {// midnight?
			rainInitial = DUMMY_VALUE; // reset midnight rain offset
		}
		period = clockMinute / LOG_INTERVAL; // set current period number
		periodLow = period; // set first period number
		for (int p = periodLow; p < PERIOD_SIZE; p++) { // go through periods
			for (int m = 0; m < MEASURE_SIZE; m++) {
				// go through measurements
				measureCount[m][p] = 0; // initialise measure count
			}
		}
	}

	/**
	 * Log buffer data.
	 * 
	 * @param prefix
	 *            explanatory prefix
	 * @param bytes
	 *            number of bytes to log
	 * @param buffer
	 *            data buffer to log
	 */
	private static void logBuffer(String prefix, int bytes, byte[] buffer) {
		String response = ""; // initialise response message
		for (int i = 0; i < bytes; i++) {
			// go through buffer bytes
			response += String.format(" %02X", buffer[i]); // append buffer byte in hex
		}
		logInfo(prefix + response); // log buffer content message
	}

	/**
	 * Log error message.
	 * 
	 * @param message
	 *            error message
	 */
	private static void logError(String message) {
		logMessage(true, message); // log error
	}

	/**
	 * Log, archive and report hourly data, updating current clock time and
	 * daate.
	 * 
	 * @param newHour
	 *            new hour
	 * @throws input
	 *             -output exception
	 */
	private static void logHour(int newHour) throws IOException {
		logMeasures(); // log measures to file
		clockMinute = 0; // set clock minute
		clockHour = newHour; // set clock hour
		reportMeasures(); // report measures to log
		Calendar now = Calendar.getInstance(); // get current date and time
		clockDay = now.get(Calendar.DAY_OF_MONTH); // get current day
		clockMonth = now.get(Calendar.MONTH) + 1; // get current month
		clockYear = now.get(Calendar.YEAR); // get current year
	}

	/**
	 * Log information message.
	 * 
	 * @param message
	 *            information message
	 */
	private static void logInfo(String message) {
		logMessage(false, message); // log information
	}

	/**
	 * Log to the day file all measurements (in index order) for each period
	 * within the current hour.
	 * 
	 * @throws input
	 *             -output exception
	 */
	private static void logMeasures() throws IOException {
		File logFile = new File(String.format("%04d%02d%02d.DAT", clockYear, clockMonth, clockDay)); // set log file
		BufferedWriter logWriter = // open log file for appending
		new BufferedWriter(new FileWriter(logFile, true));
		for (int p = periodLow; p < PERIOD_SIZE; p++) { // go through each period
			String report = String.format("%02d:%02d:00", clockHour, p * LOG_INTERVAL);  // start rep. line with HH:MM:SS
			
			for (int m = 0; m < MEASURE_SIZE; m++) { // go through measurements
				String format = measureFormat[m]; // get measurement format
				int count = measureCount[m][p]; // get measurement count
				float min = 0.0f; // init. measurement minimum
				float max = 0.0f; // init. measurement maximum
				float total = 0.0f; // init. measurement total
				float average = 0.0f; // initialise average
				if (count > 0) { // positive count?
					min = measureMin[m][p]; // get measurement minimum
					max = measureMax[m][p]; // get measurement maximum
					total = measureTotal[m][p]; // get measurement total
					average = total / count; // calculate average measure
				}
				if (m != INDEX_RAIN_TOTAL) { // not rainfall total?
					report += String.format(format + format + format, average, min, max); // output average/min/max
				}
				else {
					// rainfall total
					report += String.format(format, average);// output average rainfall
					
				}
			}
			logWriter.write(report); // output report line
			logWriter.newLine(); // append newline to log
		}
		logWriter.close(); // close log file
	}

	/**
	 * Log message.
	 * 
	 * @param error
	 *            error if true, information if false
	 * @param message
	 *            error message
	 */
	private static void logMessage(boolean error, String message) {
		if (error) {// error message?
			logger.error(message);
		} else {
			// information message
			logger.debug(message);
		}
	}

	/**
	 * Report measurements to log if required.
	 */
	private static void reportMeasures() {
		if ((logFlags & LOG_HOUR) != 0) { // hourly data to be output?
			String report = // start report with HH:MM
			String.format("%02d:%02d ", clockHour, clockMinute);
			for (int m = 0; m < MEASURE_SIZE; m++) { // go through measurements
				int count = 0; // initialise measurement count
				float total = 0.0f; // initialise measurement total
				for (int p = periodLow; p < PERIOD_SIZE; p++) { // go through periods
					int actualCount = measureCount[m][p]; // get measurement count
					if (actualCount > 0) { // non-zero measurement count?
						count += actualCount; // add measurement count
						total += measureTotal[m][p]; // add measurement total
					} else {
						// zero measurement count
						setMissingStatus(m); // set missing data status
					}
				}
				float average = 0.0f; // initialise average
				if (count > 0) {// non-zero count?
					average = total / count; // calculate average
				}
				if (m == INDEX_WIND_SPEED) { // wind speed?
					report += String.format("Wind"
							+ measureFormat[INDEX_WIND_SPEED] + "m/s"
							+ statusAnemometer, average);
				}
				else if (m == INDEX_WIND_DIRECTION) {// wind direction?
					report += String.format("Dir"
							+ measureFormat[INDEX_WIND_DIRECTION] + DEGREE
							+ ' ', average);
				}
				else if (m == INDEX_OUTDOOR_TEMPERATURE) {// outdoor temperature?
					report += String.format("Temp"
							+ measureFormat[INDEX_OUTDOOR_TEMPERATURE] + DEGREE
							+ 'C' + statusThermohygrometer, average);
				}
				else if (m == INDEX_OUTDOOR_HUMIDITY) {// outdoor humidity?
					report += String.format("Hum"
							+ measureFormat[INDEX_OUTDOOR_HUMIDITY] + "%% ",
							average);
				}
				else if (m == INDEX_INDOOR_PRESSURE) {// indoor pressure (NNNN)?
					report += String.format("Press" + "%5.0f" + "mb"
							+ statusBarometer, average);
				}
				else if (m == INDEX_RAIN_TOTAL) {// rainfall total?
					report += String.format("Rain"
							+ measureFormat[INDEX_RAIN_TOTAL] + "mm"
							+ statusRain, average);
				}
				else if (m == INDEX_UV_INDEX) {// rainfall total?
					report += String
							.format("UV" + measureFormat[INDEX_UV_INDEX]
									+ statusUV, average);
				}
			}
			logInfo(report); // output hourly report
		}
	}

	/**
	 * Return battery status symbol corresponding to sensor code and battery
	 * state description. Also set global battery status for sensor.
	 * 
	 * @param sensorCode
	 *            sensor code
	 * @param batteryDescription
	 *            battery description
	 * @return battery status symbol
	 */
	private static char setBatteryStatus(byte sensorCode, String batteryDescription) {
		char batterySymbol = // get battery status symbol
		batteryDescription.equals("OK") ? STATUS_OK : STATUS_BATTERY;
		if (batterySymbol == STATUS_BATTERY) { // battery low?
			if (sensorCode == CODE_ANEMOMETER) {// anemometer?
				statusAnemometer = batterySymbol; // set anemometer status
			}
			else if (sensorCode == CODE_RAINFALL) {// rain gauge?
				statusRain = batterySymbol; // set rain gauge status
			}
			else if (sensorCode == CODE_THERMOHYGROMETER) {// outdoor thermohygrometer?
				statusThermohygrometer = batterySymbol; // set thermohygrometer status
			}
			else if (sensorCode == CODE_UV) {// UV sensor?
				statusUV = batterySymbol; // set UV sensor status
			}
		}
		return (batterySymbol); // return battery status symbol
	}

	/**
	 * Set global missing data status for sensor corresponding to measurement
	 * index.
	 * 
	 * @param measurementIndex
	 *            measurement index
	 */
	private static void setMissingStatus(int measurementIndex) {
		if (measurementIndex == INDEX_WIND_DIRECTION ||  measurementIndex == INDEX_WIND_SPEED) {// anemometer?
			statusAnemometer = STATUS_MISSING; // set anemometer status
		}
		else if (measurementIndex == INDEX_INDOOR_PRESSURE) {// barometer?
			statusBarometer = STATUS_MISSING; // set rain gauge status
		}
		else if (measurementIndex == INDEX_RAIN_TOTAL) {// rain gauge?
			statusRain = STATUS_MISSING; // set rain gauge status
		}
		else if (measurementIndex == INDEX_OUTDOOR_HUMIDITY || measurementIndex == INDEX_OUTDOOR_TEMPERATURE) {// thermohygrometer?
				
			statusThermohygrometer = STATUS_MISSING; // set thermohygrometer status
		}
		else if (measurementIndex == INDEX_UV_INDEX) {// UV sensor?
			statusUV = STATUS_MISSING; // set UV sensor status
		}
	}

	/**
	 * Extract current day, month and year into global clock variables.
	 * 
	 * @return current calendar
	 */
	private static Calendar setDate() {
		Calendar now = Calendar.getInstance(); // get current date and time
		clockHour = now.get(Calendar.HOUR_OF_DAY); // get current hour
		clockMinute = now.get(Calendar.MINUTE); // get current minute
		clockDay = now.get(Calendar.DAY_OF_MONTH); // get current day
		clockMonth = now.get(Calendar.MONTH) + 1; // get current month
		clockYear = now.get(Calendar.YEAR); // get current year
		return (now); // return current calendar
	}

	/**
	 * Repeatedly request data from the weather station, analysing the
	 * responses. Periodically repeat the data request.
	 * 
	 * @throws input
	 *             -output exception
	 */
	public static void stationRead() throws IOException {
		active = true;
		logger.debug("Entering STATION READ loop...");
		while (active) { // loop indefinitely
			logger.debug("STATION READ");
			long actualTime = currentTime(); // get current time in msec
			if (actualTime - lastTime > STATION_TIMEOUT * 1000) {// data request timeout passed?
				logger.debug("Performing station request");
				stationRequest(); // request data from station
				lastTime = actualTime; // note last data request time
			}
			responseBytes = hidDevice.readTimeout(responseBuffer, RESPONSE_TIMEOUT * 1000);// read HID data with time limit
			analyseResponse(responseBytes, responseBuffer); // analyse HID responses
		}
		logger.debug("Exited STATION READ loop.");
	}
	
	public static void stopRead() {
		active = false;
	}
	
	/**
	 * Re-initialise the weather station then send a data request.
	 * 
	 * @throws input
	 *             -output exception
	 */
	private static void stationRequest() throws IOException {
		logger.debug("STATION REQUEST");
		if (logFlags != 0 && logFlags != LOG_HOUR) {// not just hourly data?
			logInfo("Requested weather station data");// output newline
		}
		responseBytes = hidDevice.write(STATION_INITIALISATION);// send initialisation command
		responseBytes = hidDevice.write(STATION_REQUEST);// send data request command
	}

	/**
	 * Validate checksum and frame length.
	 * 
	 * @param frame
	 *            sensor data
	 * @return true if checksum is valid
	 */
	private static boolean validFrame(byte[] frame) {
		int length = frame.length; // get frame length
		int byteSum = 0; // initialise byte sum
		boolean valid = false; // overall validity
		if (length >= 2) { // at least a two-byte frame?
			for (int i = 0; i < length - 2; i++) {// go through non-checksum bytes
				byteSum += getInt(frame[i]); // add byte to sum
			}
			int checkSum = 256 * getInt(frame[length - 1]) + getInt(frame[length - 2]);// get expected sum
			
			valid = checkSum == byteSum; // get checksum validity
			if (valid) { // checksum valid?
				Byte sensorCode = frame[1]; // get sensor code
				Integer sensorLength = responseLength.get(sensorCode);// get sensor frame length
				valid = sensorLength != null && sensorLength.intValue() == length;// get length validity
			}
		}
		// else // not at least a two-byte frame
		// logError("Short frame with length " + length);
		return (valid); // return validity check
	}
	
	/**
	 * Associate a HID Device to the logger.
	 * @param device
	 */
	public static void setDevice(HIDDevice device) {
		WxLogger.hidDevice = device;
	}
	
	/**
	 * Interface defining a listener for weather data.
	 * @author Jerome
	 *
	 */
	public static interface DataListener {
		void processData(Map<String, Object> data);
	}
	
	protected static List<DataListener> listeners = new ArrayList<DataListener>();
	
	/**
	 * Add a listener to the listeners pool. 
	 * @param l {@link DataListener}
	 */
	public static void addDataListener(DataListener l) {
		listeners.add(l);
	}
	
	/**
	 * Remove a listener from the listeners pool.
	 * @param l {@link DataListener}
	 */
	public static void removeListener(DataListener l) {
		listeners.remove(l);
	}
	
	/**
	 * Remove all listeners from the listeners pool at once.
	 */
	public static void clearListeners() {
		listeners.clear();
	}
	
	/**
	 * Push data towards registered listeners.
	 */
	protected static void pushData() {
		for (DataListener l : listeners) {
			l.processData(WxLogger.DATA);
		}
	}

}
