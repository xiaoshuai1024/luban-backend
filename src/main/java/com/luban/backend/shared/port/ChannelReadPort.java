package com.luban.backend.shared.port;

import com.luban.backend.shared.entity.Channel;

import java.util.Optional;

/**
 * 渠道只读 Port（publicside 依赖倒置）。
 *
 * <p>publicside 短链解析需按 short_url 查渠道，但不能直连 {@code ChannelMapper}。
 * Port 接口置于 shared，实现置于 operatorside，publicside 仅依赖接口。
 */
public interface ChannelReadPort {

    /** 短链解析：按 short_url 查渠道。 */
    Optional<Channel> getByShortUrl(String shortUrl);
}
