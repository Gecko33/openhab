/**
 * 
 */
package org.openhab.binding.wmr100.utils;

/**
 * @author Jerome
 *
 */
public class WMRUtils {


	/**
	 * Return current time in msec
	 * 
	 * @return current time in msec
	 */
	public static long currentTime() {
		return (System.nanoTime() / 1000000); // return current time in msec
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
	public static String getDate(int year, int month, int day) {
		return (String.format("%02d/%02d/%04d", day, month, year));  // return DD/MM/YYYY
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
	public static String getTime(int hour, int minute) {
		return (String.format("%02d:%02d", hour, minute)); // return HH:MM
	}

	/**
	 * Return rainfall in inches.
	 * 
	 * @param rain
	 *            rainfall in 100ths of inches
	 * @return rainfall in mm
	 */
	public static float getRain(float rain) {
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
	public static int getSign(int signCode) {
		return (signCode == 0 ? +1 : -1); // return sign code
	}

	/**
	 * Return integer value of byte.
	 * 
	 * @param value
	 *            byte value
	 * @return integer value
	 */
	public static int getInt(byte value) {
		return ((int) value & 0xFF); // return bottom 8 bits
	}


	/**
	 * Return description corresponding to wind direction code.
	 * 
	 * @param directionCode
	 *            wind direction code
	 * @return wind direction description
	 */
	public static String getDirection(int directionCode) {
		return (directionCode < WMRConstants.DIRECTION_DESCRIPTION.length 
						? WMRConstants.DIRECTION_DESCRIPTION[directionCode] 
						: "Unknown");// return wind direction descr. // weather dir. in range?
	}

	/**
	 * Return description corresponding to battery code.
	 * 
	 * @param batteryCode
	 *            battery code
	 * @return battery description
	 */
	public static String getBattery(int batteryCode) {
		int batteryIndex = batteryCode == 0 ? 0 : 1;// get battery description index
		return (WMRConstants.BATTERY_DESCRIPTION[batteryIndex]); // return battery description
	}

	/**
	 * Return description corresponding to radio code.
	 * 
	 * @param radioCode
	 *            radio code
	 * @return radio description
	 */
	public static String getRadio(int radioCode) {
		return (radioCode < WMRConstants.RADIO_DESCRIPTION.length ? WMRConstants.RADIO_DESCRIPTION[radioCode] : "Strong");// return radio description // radio code in range?
	}

	/**
	 * Return description corresponding to trend code.
	 * 
	 * @param trendCode
	 *            trend code
	 * @return trend description
	 */
	public static String getTrend(int trendCode) {
		return (trendCode < WMRConstants.TREND_DESCRIPTION.length ? WMRConstants.TREND_DESCRIPTION[trendCode] : "Unknown"); // return trend description// trend code in range?
	}

	/**
	 * Return description corresponding to UV code.
	 * 
	 * @param uvCode
	 *            uvCode code
	 * @return uvCode description
	 */
	public static String getUV(int uvCode) {
		int uvIndex = (uvCode >= 11 ? 4 : uvCode >= 8 ? 3 : uvCode >= 6 ? 2 : uvCode >= 3 ? 1 : 0);
		return (WMRConstants.UV_DESCRIPTION[uvIndex]); // get UV description index // return UV description
	}

	/**
	 * Return description corresponding to weather code.
	 * 
	 * @param weatherCode
	 *            weather code
	 * @return weather description
	 */
	public static String getWeather(int weatherCode) {
		return (weatherCode < WMRConstants.WEATHER_DESCRIPTION.length ? WMRConstants.WEATHER_DESCRIPTION[weatherCode]	: "Unknown"); // return weather description // weather code in range?
	}

	/**
	 * Return Celsius equivalent of Fahrenheit temperature.
	 * 
	 * @param fahrenheit
	 *            Fahrenheit temperature
	 * @return Celsius temperature
	 */
	public static float fahrenheitCelsius(float fahrenheit) {
		return (0.5555f * (fahrenheit - 32.0f)); // return Celsius equivalent
	}
}
