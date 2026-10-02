package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.QueueMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueMemberRepository extends JpaRepository<QueueMember, String> {

    List<QueueMember> findByQueueId(String queueId);

    List<QueueMember> findByUserId(String userId);

    Optional<QueueMember> findByQueueIdAndUserId(String queueId, String userId);

    long deleteByQueueIdAndUserId(String queueId, String userId);

    @Query("SELECT m.queueId, COUNT(m) FROM QueueMember m WHERE m.queueId IN :queueIds GROUP BY m.queueId")
    List<Object[]> countByQueueIds(@Param("queueIds") Collection<String> queueIds);
}
