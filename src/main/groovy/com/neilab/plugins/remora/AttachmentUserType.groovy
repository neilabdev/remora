package com.neilab.plugins.remora


/* */
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.ParameterizedType;
import org.hibernate.usertype.UserType;

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

/**
 * Created by ghost on 7/29/15.
 */
class AttachmentUserType implements UserType {

    @Override
    int[] sqlTypes() {
        return [Types.LONGVARCHAR]
    }

    @Override
    Class returnedClass() {
        return Attachment
    }

    @Override
    Object assemble(Serializable cached, Object owner) throws HibernateException {
        return cached
    }

    @Override
    Serializable disassemble(Object value) throws HibernateException {
        return (Serializable) value
    }

    @Override
    boolean equals(Object x, Object y) throws HibernateException {
        return x == null ? y == null : x.equals(y)
    }


   @Override
    int hashCode(Object value) throws HibernateException {
        return value == null ? 0 : value.hashCode()
    }
/*

    @Override
    Object nullSafeGet(ResultSet rs, String[] names, Object o) throws HibernateException, SQLException {
        def result = rs.getString(names[0])
        return result ? new Attachment(result) : null
    }

    @Override
    void nullSafeSet(PreparedStatement st, Object value, int index) throws HibernateException, SQLException {
        if (value instanceof Attachment) {
            Attachment attachment = value
            st.setString(index, attachment.toJson() as String)
        } else if (value instanceof String) {
            st.setString(index, value.toString() as String)
        } else {
            st.setNull(index, Types.LONGVARCHAR)
        }
    } */
/* Grails 4.0 */
    @Override //new method
    Object nullSafeGet(ResultSet rs, String[] names, org.hibernate.engine.spi.SharedSessionContractImplementor session, Object owner) throws HibernateException, SQLException {
        def result = rs.getString(names[0])
        return result ? new Attachment(result) : null
    }

    @Override //new method
    void nullSafeSet(PreparedStatement st, Object value, int index, org.hibernate.engine.spi.SharedSessionContractImplementor session) throws HibernateException, SQLException {
        if (value instanceof Attachment) {
            Attachment attachment = value
            st.setString(index, attachment.toJson() as String)
        } else if (value instanceof String) {
            st.setString(index, value.toString() as String)
        } else {
            st.setNull(index, Types.LONGVARCHAR)
        }
    }

    @Override
    Object deepCopy(Object o) throws HibernateException {
        return o
    }

    @Override
    boolean isMutable() {
        return false
    }


    @Override
    Object replace(Object o, Object o1, Object o2) throws HibernateException {
        return null
    }

    // new methods


}
