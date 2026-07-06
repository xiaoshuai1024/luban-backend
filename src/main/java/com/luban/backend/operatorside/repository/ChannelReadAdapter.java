package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.mapper.ChannelMapper;
import com.luban.backend.shared.port.ChannelReadPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link ChannelReadPort} 实现：封装 {@link ChannelMapper#getByShortUrl}。
 * publicside 短链解析经此 Port，不直连 Mapper。
 */
@Component
public class ChannelReadAdapter implements ChannelReadPort {

    private final ChannelMapper channelMapper;

    public ChannelReadAdapter(ChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    @Override
    public Optional<Channel> getByShortUrl(String shortUrl) {
        return Optional.ofNullable(channelMapper.getByShortUrl(shortUrl));
    }
}
