package com.docauth.repository;

import com.docauth.entity.VisibleDeptRel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VisibleDeptRelRepository extends JpaRepository<VisibleDeptRel, Long> {

    /** Query all visible departments bound to a given role/user. */
    List<VisibleDeptRel> findByRelTypeAndRelId(String relType, Long relId);

    /** Check whether a role/user already bound a department (for de-duplication). */
    Optional<VisibleDeptRel> findByRelTypeAndRelIdAndDeptId(String relType, Long relId, Long deptId);

    /** Reverse lookup: which roles/users set this department as visible. */
    List<VisibleDeptRel> findByDeptId(Long deptId);

    /** Cascade cleanup: remove all bindings of a role/user. */
    void deleteByRelTypeAndRelId(String relType, Long relId);

    /** Cascade cleanup: remove bindings when a department is deleted. */
    void deleteByDeptId(Long deptId);
}
