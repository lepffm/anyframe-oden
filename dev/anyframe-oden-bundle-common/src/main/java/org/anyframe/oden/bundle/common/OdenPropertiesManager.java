/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.anyframe.oden.bundle.common;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * OdenPropertiesmanagerFactory 통해 접근할 것 
 * OdenPropertiesManager는 기존 PropertiesUtil에 멀티스레드 기능 추가함
 * OdenPropertiesManager가 관리하는 properties파일은 동시에 하나의 스레드만 접근 가능
 * 
 * @author Junghwan Hong
 */
public class OdenPropertiesManager {
	private String name = null;

	OdenPropertiesManager(String name) {
		this.name = name;
	}

	@SuppressWarnings("PMD")
	public synchronized Properties loadProperties() throws OdenException {
		try {
			return PropertiesUtil.loadProperties(name);
		} catch (FileNotFoundException e) {
			throw new OdenException("Couldn't find a properties file: " + name);
		} catch (IOException e) {
			throw new OdenException("Illegal properties format: " + name);
		}
	}

	@SuppressWarnings("PMD")
	public synchronized String getKeys() throws OdenException {
		try {
			return PropertiesUtil.getKeys(name);
		} catch (FileNotFoundException e) {
			throw new OdenException("Couldn't find that file: " + name);
		} catch (IOException e) {
			throw new OdenException("Illegal properties format: " + name);
		}
	}

	@SuppressWarnings("PMD")
	public synchronized void storeProperties(Properties prop)
			throws OdenException {
		try {
			PropertiesUtil.storeProperties(name, prop);
		} catch (FileNotFoundException e) {
			throw new OdenException("Couldn't find a properties file: " + name);
		} catch (IOException e) {
			throw new OdenException("Illegal properties format: " + prop);
		}
	}

	@SuppressWarnings("PMD")
	public synchronized String getProp(String key) throws OdenException {
		Properties prop = loadProperties();
		return prop.getProperty(key);
	}

	@SuppressWarnings("PMD")
	public synchronized void addProp(String key, String value)
			throws OdenException {
		Properties prop = loadProperties();
		prop.put(key, value);
		storeProperties(prop);
	}
	
	@SuppressWarnings("PMD")
	public synchronized void removeProp(String key) throws OdenException {
		Properties prop = loadProperties();
		prop.remove(key);
		storeProperties(prop);
	}
	
	@SuppressWarnings("PMD")
	public synchronized String toString(String name) throws OdenException {
		try {
			return PropertiesUtil.toString(name);
		} catch (FileNotFoundException e) {
			throw new OdenException("Couldn't find a properties file: " + name);
		} catch (IOException e) {
			throw new OdenException("Illegal properties format: " + name);
		}
	}
}