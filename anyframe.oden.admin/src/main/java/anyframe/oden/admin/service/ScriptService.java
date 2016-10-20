/*
 * Copyright 2010 SAMSUNG SDS Co., Ltd. All rights reserved.
 *
 * No part of this "source code" may be reproduced, stored in a retrieval
 * system, or transmitted, in any form or by any means, mechanical,
 * electronic, photocopying, recording, or otherwise, without prior written
 * permission of SAMSUNG SDS Co., Ltd., with the following exceptions:
 * Any person is hereby authorized to store "source code" on a single
 * computer for personal use only and to print copies of "source code"
 * for personal use provided that the "source code" contains SAMSUNG SDS's
 * copyright notice.
 *
 * No licenses, express or implied, are granted with respect to any of
 * the technology described in this "source code". SAMSUNG SDS retains all
 * intellectual property rights associated with the technology described
 * in this "source code".
 *
 */
package anyframe.oden.admin.service;

import java.util.List;

import anyframe.common.Page;
import anyframe.oden.admin.domain.Command;



/**
 * @version 1.0
 * @created 14-7-2010 ���� 10:13:36
 * @author LEE Sujeong
 */
public interface ScriptService {

	/**
	 * 
	 * @param domain
	 * @throws Exception 
	 */
	public Page findListByPk(String domain, String opt) throws Exception;
	
	public String run(String[]param, String name, String script) throws Exception;
	
	public List<Command> getCommandList(String jobName) throws Exception;

}