package org.openhab.binding.wmr100.utils;

public interface WMRConstants {

	/** Index of indoor dewpoint measurements (Celsius) */
	static final int INDEX_INDOOR_DEWPOINT = 10;
	/** Index of indoor humidity measurements (%) */
	static final int INDEX_INDOOR_HUMIDITY = 7;
	/** Index of indoor pressure measurements (mbar) */
	static final int INDEX_INDOOR_PRESSURE = 5;
	/** Index of indoor temperature measurements (Celsius) */
	static final int INDEX_INDOOR_TEMPERATURE = 6;
	/** Index of outdoor dewpoint measurements (Celsius) */
	static final int INDEX_OUTDOOR_DEWPOINT = 4;
	/** Index of outdoor humidity measurements (%) */
	static final int INDEX_OUTDOOR_HUMIDITY = 3;
	/** Index of outdoor temperature measurements (Celsius) */
	static final int INDEX_OUTDOOR_TEMPERATURE = 2;
	/** Index of UV index measurements (0..) */
	static final int INDEX_UV_INDEX = 11;
	/** Index of rainfall measurements (mm) */
	static final int INDEX_RAIN_TOTAL = 8;
	/** Index of wind chill measurements (Celsius) */
	static final int INDEX_WIND_CHILL = 9;
	/** Index of wind direction measurements (degrees) */
	static final int INDEX_WIND_DIRECTION = 1;
	/** Index of wind speed measurements (metres/sec) */
	static final int INDEX_WIND_SPEED = 0;
	/** Dummy value for initialisation */
	static final float DUMMY_VALUE = -999.0f;
	/** Weather station data frame delimiter */
	static final int FRAME_BYTE = (byte) 0xFF;
	/** Console log output has frames and timestamped messages as received */
	static final int LOG_FRAME = (byte) 0x02;
	/** Console log output has hourly data */
	static final int LOG_HOUR = (byte) 0x08;
	/** Console log output has sensor data as received */
	static final int LOG_SENSOR = (byte) 0x04;
	/** Console log output has raw USB data as received */
	static final int LOG_USB = (byte) 0x01;
	/** Do not log anything */
	static final int LOG_NONE = (byte) 0x00;
	/** Battery description (indexed by battery code) */
	static final String[] BATTERY_DESCRIPTION = { "OK", "Low" };

	/** Current century */
	static final int CENTURY = 2000;

	/** Trend description (indexed by trend code) */
	static final String[] DIRECTION_DESCRIPTION = { "N", "NNE", "NE", "ENE",
			"E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW",
			"NNW" };

	/** Radio description (indexed by radio code) */
	static final String[] RADIO_DESCRIPTION = { "None", "Searching/Weak",
			"Average", "Strong" };

	/** Seconds past the hour for logging (for a margin of error in timing) */
	static final int SECOND_OFFSET = 2;

	/** Trend description (indexed by trend code) */
	static final String[] TREND_DESCRIPTION = { "Steady", "Rising", "Falling" };

	/** UV description (indexed by UV code) */
	static final String[] UV_DESCRIPTION = { "Low", "Medium", "High",
			"Very High", "Extremely High" };

	/** Weather description (indexed by weather code) */
	static final String[] WEATHER_DESCRIPTION = { "Partly Cloudy", "Rainy",
			"Cloudy", "Sunny", "?", "Snowy" };

}