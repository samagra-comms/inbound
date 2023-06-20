package com.uci.inbound;

import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.bot.util.BotUtil;
import lombok.extern.java.Log;
import messagerosa.core.model.XMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Log
@RestController
public class TestController {

    @Autowired
    public XMessageRepository xMessageRepository;
    LocalDateTime yesterday = LocalDateTime.now().minusMonths(1L);

    long totalSuccessCount = 0;

    @GetMapping("/testInboundCass")
    public void getLastMsg(@RequestParam(value = "repeat", required = false) String repeat) throws InterruptedException {
        int count = 10;

        try {
            count = Integer.parseInt(repeat);
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        }
        for (int i = 0; i < count; i++) {
            int finalI = i;
            getLatestXMessage("9783246247", yesterday, "SENT")
                    .doOnError(new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) {
                            log.severe("Error in getLatestXMessage : " + throwable.getMessage());
                        }
                    })
                    .subscribe(new Consumer<XMessageDAO>() {
                        @Override
                        public void accept(XMessageDAO xMsgDao) {
                            totalSuccessCount++;
                            log.info("TestController:found xMsgDao : " + xMsgDao.getMessageId() + " count: " + totalSuccessCount);
                        }
                    });
        }

//        for (int i = 0; i < count; i++) {
//
//            int finalI = i;
//            getLatestXMessage("9783246247", yesterday, "SENT").doOnNext(lastMessageID -> {
//                log.info(lastMessageID.getMessageId() + "  Count : " + finalI);
//            }).subscribe();
//            Thread.sleep(1000);
//        }
    }

    private Mono<XMessageDAO> getLatestXMessage(String userId, LocalDateTime yesterday, String messageState) {
        return xMessageRepository.findFirstByUserIdInAndFromIdInAndMessageStateInAndTimestampAfterOrderByTimestampDesc(List.of(BotUtil.adminUserId, userId), List.of(BotUtil.adminUserId, userId), List.of("SENT", "REPLIED"), yesterday)
                .collectList()
                .map(new Function<List<XMessageDAO>, XMessageDAO>() {
                    @Override
                    public XMessageDAO apply(List<XMessageDAO> xMessageDAOS) {
                        log.info("xMsgDaos size: " + xMessageDAOS.size() + ", messageState.name: " + XMessage.MessageState.SENT.name());
                        if (xMessageDAOS.size() > 0) {
                            List<XMessageDAO> filteredList = new ArrayList<>();
                            for (XMessageDAO xMessageDAO : xMessageDAOS) {
                                if (xMessageDAO.getMessageState().equals(XMessage.MessageState.SENT.name())
                                        || xMessageDAO.getMessageState().equals(XMessage.MessageState.REPLIED.name())) {
                                    filteredList.add(xMessageDAO);
                                }

                            }
                            if (filteredList.size() > 0) {
                                filteredList.sort(new Comparator<XMessageDAO>() {
                                    @Override
                                    public int compare(XMessageDAO o1, XMessageDAO o2) {
                                        return o1.getTimestamp().compareTo(o2.getTimestamp());
                                    }
                                });
                            }

                            return xMessageDAOS.get(0);
                        }
                        return new XMessageDAO();
                    }
                });
    }
}
