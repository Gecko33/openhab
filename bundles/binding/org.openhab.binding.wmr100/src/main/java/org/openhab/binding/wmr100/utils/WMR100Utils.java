/**
 * 
 */
package org.openhab.binding.wmr100.utils;

/**
 * @author Jerome
 *
 */
public class WMR100Utils {
	
	/**
	 * Converts an array of bytes to a human readable string.
	 * @param a
	 * @return
	 */
	public static String bytArrayToHex(byte[] a) {
		StringBuilder sb = new StringBuilder();
		for(byte b: a)
			sb.append(String.format("%02x", b&0xff));
		return sb.toString();
	}

}
