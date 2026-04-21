package com.alsystem.casemanager.service;

import java.io.IOException;

public interface XmindService {

    /**
     * 将md内容转换成xmind文件
     * @param content
     * @return
     */
    byte[] convertMdToXmind(String content) throws IOException;
}
