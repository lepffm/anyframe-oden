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
package org.anyframe.oden.admin.service;

import org.anyframe.pagination.Page;

/**
 * This is StatusService Interface
 * 
 * @author Junghwan Hong
 */
public interface StatusService {

	/**
	 * 
	 * @param domain
	 * @throws Exception
	 */
	public Page findList(String domain) throws Exception;
	
	/**
	 * 
	 * @param domain
	 * @throws Exception
	 */
	public boolean checkRunning(String domain) throws Exception;

	/**
	 * 
	 * @param param
	 * @throws Exception
	 */
	public String stop(String param) throws Exception;

}