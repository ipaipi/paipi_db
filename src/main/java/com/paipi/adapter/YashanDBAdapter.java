package com.paipi.adapter;

import com.paipi.common.channel.Channel;
import com.paipi.common.channel.Record;
import com.paipi.config.Configuration;

import java.util.List;

//todo YashanDB(崖山)适配
//官方文档 https://doc.yashandb.com/yashandb/22.2/zh/%E4%BA%A7%E5%93%81%E6%8F%8F%E8%BF%B0/%E4%BA%A7%E5%93%81%E7%AE%80%E4%BB%8B.html
//ymp C:\Users\paipi\Downloads\yashan-migrate-platform-23.4.3.2-linux-x86-64\yashan-migrate-platform\lib
public class YashanDBAdapter extends MysqlAdapter {

    public YashanDBAdapter(Configuration originalConfig, Configuration config) {
        super(originalConfig, config);
    }

    @Override
    public String getDriverName() {
        return "com.yashandb.jdbc.Driver";
    }

    @Override
    public String getJdbcUrl() {
        return "yasdb://" + this.ip + ":" + this.port
                + "?" + assembleJdbcUrlParam();
    }

    @Override
    public void readerPlugin(Channel<Record> channel) throws Exception {
        super.readerPlugin(channel);
    }

    @Override
    public void writerPlugin(Channel<Record> channel) throws Exception {
        super.writerPlugin(channel);
    }

    @Override
    public List<Configuration> split(int adviceNumber) throws Exception {
        return super.split(adviceNumber);
    }
}
