package com.uci.inbound.xmsg;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.datastax.oss.driver.api.core.cql.PagingState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Optional;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;

import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.XMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/xmsg")
public class XMessageController {
	@Autowired
	private XMessageRepository xMsgRepo;
	
	@Value("${xmessage.user.data.before.hours}")
	private Float xMessageUserDataBeforehours;

	@RequestMapping(value = "/user/dataByUserId/{userid}/{fromid}", method = RequestMethod.GET, produces = { "application/json",
			"text/json" })
	public Flux<Slice<XMessageDAO>> getUserDataByUserId(@PathVariable("userid") String userid, @PathVariable("fromid") String fromid)
			throws JsonMappingException, JsonProcessingException {
		log.info("Called getUserDataByUserId");
		
    	Pageable paging = (Pageable) CassandraPageRequest.of(
    			PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "timestamp")),
    			null
    	     );
		
    	return xMsgRepo.findAllByUserIdAndFromId(paging, userid, fromid);
		
	}

	@RequestMapping(value = "/user/dataByUserId/{userid}/{fromid}", method = RequestMethod.DELETE, produces = { "application/json",
	"text/json" })
	public void deleteUserDataAllByUserId(@PathVariable("userid") String userid, @PathVariable("fromid") String fromid)
		throws JsonMappingException, JsonProcessingException {
		log.info("Called deleteUserDataAllByUserId");
		
    	Pageable paging = (Pageable) CassandraPageRequest.of(
    			PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "timestamp")),
    			null
    	     );
		
		xMsgRepo.findAllByUserIdAndFromId(paging, userid, fromid).subscribe(pagedResult -> {
			List<XMessageDAO> xMsgDaos = pagedResult.getContent();
			log.info("totalSize: "+pagedResult.getSize()
				+", totalItems: "+pagedResult.getNumberOfElements()
				+", currentPage: 0");
			xMsgDaos.forEach(xMsgDao -> {
				log.info("Delete xMsgDao id: "+xMsgDao.getId());
				xMsgRepo.delete(xMsgDao).subscribe();
			});
		});
	}

	@RequestMapping(value = "/user/dataByUserIdBeforeHours/{userid}", method = RequestMethod.GET, produces = { "application/json",
			"text/json" })
	public Flux<XMessageDAO> getUserDataByUserIdBeforeHours(@PathVariable("userid") String userid) {
		log.info("Called getUserDataByUserIdBeforeHours");
		
		long beforeMins = (long) (xMessageUserDataBeforehours * 60);
		LocalDateTime timestamp = LocalDateTime.now().minusMinutes(beforeMins);
		
		log.info("Timestamp: "+timestamp);
		return xMsgRepo.findAllByUserIdAndTimestampAfter(userid, timestamp);
	}
	

	
	@RequestMapping(value = "/user/dataByUserIdBeforeHours/{userid}", method = RequestMethod.DELETE, produces = {
			"application/json", "text/json" })
	public void deleteAllUserDataByUserIdBeforeHours(@PathVariable("userid") String userid) {
		log.info("Called deleteAllUserDataByUserIdBeforeHours");
		
		long beforeMins = (long) (xMessageUserDataBeforehours * 60);
		LocalDateTime timestamp = LocalDateTime.now().minusMinutes(beforeMins);
		
		log.info("Timestamp: "+timestamp);
		xMsgRepo.findAllByUserIdAndTimestampAfter(userid, timestamp).subscribe(xMsgDao -> {
			xMsgRepo.delete(xMsgDao).subscribe();
		});
	}

	@RequestMapping(value = "/user/dataById/{id}", method = RequestMethod.DELETE, produces = {
			"application/json", "text/json" })
	public void deleteUserDataById(@PathVariable("id") String id) {
		log.info("Called deleteUserDataById");
		
		xMsgRepo.findById(UUID.fromString(id)).subscribe(xMsgDao -> {
			xMsgRepo.delete(xMsgDao).subscribe();
		});
	}
}
