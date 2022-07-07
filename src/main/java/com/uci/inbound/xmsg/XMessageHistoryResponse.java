package com.uci.inbound.xmsg;

import com.uci.dao.models.XMessageDAO;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import javax.annotation.Nullable;
import java.util.List;

@Getter
@Setter
public class XMessageHistoryResponse {
    Integer statusCode;

    @Nullable
    String errorMsg;

    List<XMessageDAO> records;

    @Nullable
    String prevCursorMark;

    @Nullable
    String nextCursorMark;
}
