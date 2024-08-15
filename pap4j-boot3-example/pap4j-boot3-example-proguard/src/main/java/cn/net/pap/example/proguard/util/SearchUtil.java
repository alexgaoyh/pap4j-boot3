package cn.net.pap.example.proguard.util;

import cn.net.pap.example.proguard.util.dto.SearchConditionDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

public class SearchUtil {

    /**
     * 高级查询
     *
     * @param conditions
     * @param entityManager
     * @param entityClass
     * @param <T>
     * @return
     */
    public static <T> List<T> filterEntities(List<SearchConditionDTO> conditions, EntityManager entityManager, Class<T> entityClass) {
        TypedQuery<T> query = geneQuery(conditions, entityManager, entityClass);
        return query.getResultList();
    }

    /**
     * 高级查询 分页
     *
     * @param conditions
     * @param entityManager
     * @param entityClass
     * @param pageNumber
     * @param pageSize
     * @param <T>
     * @return
     */
    public static <T> List<T> filterEntities(List<SearchConditionDTO> conditions, EntityManager entityManager, Class<T> entityClass, int pageNumber, int pageSize) {

        TypedQuery<T> query = geneQuery(conditions, entityManager, entityClass);
        query.setFirstResult((pageNumber - 1) * pageSize);
        query.setMaxResults(pageSize);
        return query.getResultList();
    }


    private static <T> TypedQuery<T> geneQuery(List<SearchConditionDTO> conditions, EntityManager entityManager, Class<T> entityClass) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);

        List<Predicate> predicates = new ArrayList<>();

        for (SearchConditionDTO condition : conditions) {
            switch (condition.getOperator()) {
                case EQUAL:
                    predicates.add(cb.equal(root.get(condition.getField()), condition.getValue()));
                    break;
                case NOT_EQUAL:
                    predicates.add(cb.notEqual(root.get(condition.getField()), condition.getValue()));
                    break;
                case LIKE:
                    predicates.add(cb.like(root.get(condition.getField()), "%" + condition.getValue() + "%"));
                    break;
                case NOT_LIKE:
                    predicates.add(cb.notLike(root.get(condition.getField()), "%" + condition.getValue() + "%"));
                    break;
                case GREATER_THAN:
                    predicates.add(cb.greaterThan(root.get(condition.getField()), (Comparable) condition.getValue()));
                    break;
                case GREATER_THAN_OR_EQUAL_TO:
                    predicates.add(cb.greaterThanOrEqualTo(root.get(condition.getField()), (Comparable) condition.getValue()));
                    break;
                case LESS_THAN:
                    predicates.add(cb.lessThan(root.get(condition.getField()), (Comparable) condition.getValue()));
                    break;
                case LESS_THAN_OR_EQUAL_TO:
                    predicates.add(cb.lessThanOrEqualTo(root.get(condition.getField()), (Comparable) condition.getValue()));
                    break;
                case GT:
                    predicates.add(cb.gt(root.get(condition.getField()), (Number) condition.getValue()));
                    break;
                case LT:
                    predicates.add(cb.lt(root.get(condition.getField()), (Number) condition.getValue()));
                    break;
                case GE:
                    predicates.add(cb.ge(root.get(condition.getField()), (Number) condition.getValue()));
                    break;
                case LE:
                    predicates.add(cb.le(root.get(condition.getField()), (Number) condition.getValue()));
                    break;
            }
        }

        cq.where(cb.and(predicates.toArray(new Predicate[0])));

        TypedQuery<T> query = entityManager.createQuery(cq);
        return query;
    }

}
