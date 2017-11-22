package com.bol.system;

import com.bol.crypt.CryptVault;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Base64;
import java.util.List;

import static com.bol.crypt.CryptVault.fromSignedByte;
import static com.bol.system.MyBean.MONGO_NONSENSITIVEDATA;
import static com.bol.system.MyBean.MONGO_SECRETSTRING;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** needs mongodb running locally; fixme: use embedmongo */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {MongoDBConfiguration.class})
public class CryptVersionSystemTest {

    @Autowired MongoTemplate mongoTemplate;
    @Autowired CryptVault cryptVault;

    @Before
    public void setup() {
        mongoTemplate.dropCollection(MyBean.class);
    }

    @Test
    public void checkDefaultEncryptVersion() {
        cryptVault
                .with256BitAesCbcPkcs5PaddingAnd128BitSaltKey(1, Base64.getDecoder().decode("aic7QGYCCSHyy7gYRCyNTpPThbomw1/dtWl4bocyTnU="))
                .with256BitAesCbcPkcs5PaddingAnd128BitSaltKey(2, Base64.getDecoder().decode("IqWTpi549pJDZ1kuc9HppcMxtPfu2SP6Idlh+tz4LL4="));

        // default key version should now be 2
        byte[] result = cryptedResultInDb("1234");
        assertThat(result.length, is(cryptVault.expectedCryptedLength(4 + 12)));
        assertThat(fromSignedByte(result[0]), is(2));
    }

    @Test
    public void checkMultipleEncryptVersion() {
        // default key version should now be 2
        byte[] result1 = cryptedResultInDb("versioning test");

        cryptVault.with256BitAesCbcPkcs5PaddingAnd128BitSaltKey(1, Base64.getDecoder().decode("aic7QGYCCSHyy7gYRCyNTpPThbomw1/dtWl4bocyTnU="));
        byte[] result2 = cryptedResultInDb("versioning test");

        cryptVault.with256BitAesCbcPkcs5PaddingAnd128BitSaltKey(2, Base64.getDecoder().decode("IqWTpi549pJDZ1kuc9HppcMxtPfu2SP6Idlh+tz4LL4="));
        byte[] result3 = cryptedResultInDb("versioning test");

        assertThat(fromSignedByte(result1[0]), is(0));
        assertThat(fromSignedByte(result2[0]), is(1));
        assertThat(fromSignedByte(result3[0]), is(2));

        // sanity check that all of the versions are encrypted
        List<MyBean> all = mongoTemplate.find(query(where(MONGO_SECRETSTRING).is("versioning test")), MyBean.class);
        assertThat(all, hasSize(0));

        all = mongoTemplate.find(query(where(MONGO_NONSENSITIVEDATA).is(getClass().getSimpleName())), MyBean.class);
        assertThat(all, hasSize(3));

        // check that all 3 different versions are decrypted
        for (MyBean bean : all) {
            assertThat(bean.secretString, is("versioning test"));
        }
    }

    byte[] cryptedResultInDb(String value) {
        MyBean bean = new MyBean();
        bean.secretString = value;
        bean.nonSensitiveData = getClass().getSimpleName();
        mongoTemplate.insert(bean);

        DBObject fromMongo = mongoTemplate.getCollection(MyBean.MONGO_MYBEAN).find(new BasicDBObject("_id", new ObjectId(bean.id))).next();
        Object cryptedSecret = fromMongo.get(MONGO_SECRETSTRING);
        assertThat(cryptedSecret, is(instanceOf(byte[].class)));
        return (byte[]) cryptedSecret;
    }
}
