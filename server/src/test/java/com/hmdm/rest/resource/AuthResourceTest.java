/*
 * MDMesh: fork of Headwind MDM. Apache-2.0.
 */
package com.hmdm.rest.resource;

import com.hmdm.auth.HmdmAuthInterface;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.mapper.CommonMapper;
import com.hmdm.persistence.mapper.CustomerMapper;
import com.hmdm.persistence.mapper.UserMapper;
import com.hmdm.rest.json.AuthOptionsResponse;
import com.hmdm.rest.json.Response;
import com.hmdm.rest.json.UserCredentials;
import com.hmdm.rest.json.view.user.UserView;
import com.hmdm.service.EmailService;
import com.hmdm.testsupport.Fakes;
import com.hmdm.util.BackgroundTaskRunnerService;
import com.hmdm.util.PasswordResetTokens;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** POST /public/auth/login and GET /public/auth/options. */
public class AuthResourceTest {

    private User stored;
    private Fakes.FakeMapper<UserMapper> users;
    private AuthResource resource;

    @Before
    public void setUp() {
        stored = new User();
        stored.setId(1);
        stored.setLogin("admin");
        stored.setCustomerId(1);
        stored.setUserRole(new UserRole());
        stored.setAuthToken("session-token");

        users = new Fakes.FakeMapper<>(UserMapper.class);
        Fakes.FakeMapper<CommonMapper> settings = new Fakes.FakeMapper<>(CommonMapper.class);
        Fakes.FakeMapper<CustomerMapper> customers = new Fakes.FakeMapper<>(CustomerMapper.class);
        UnsecureDAO unsecureDAO = new UnsecureDAO(null, users.mapper, null, settings.mapper, null, null, null, null,
                null, null, null, null, customers.mapper, null, ".", 2, "com.hmdm.launcher");
        HmdmAuthInterface auth = new HmdmAuthInterface() {
            @Override
            public User findUser(String login) {
                return "admin".equals(login) ? stored : null;
            }

            @Override
            public boolean authenticate(User user, String password) {
                return "right".equals(password);
            }
        };
        BackgroundTaskRunnerService tasks = new BackgroundTaskRunnerService() {
            @Override
            public void submitTask(Runnable task) {
            }
        };
        EmailService email = new EmailService("smtp.example.com", 25, false, false, "", "", "", "", "mdm@localhost",
                "", "", "", "");
        resource = new AuthResource(unsecureDAO, null, unsecureDAO, tasks, email, null, false, auth);
    }

    @Test
    public void signupIsNeverOffered() {
        Response response = resource.options();

        assertFalse(((AuthOptionsResponse) response.getData()).isSignup());
        assertTrue("recovery is still offered with SMTP set", ((AuthOptionsResponse) response.getData()).isRecover());
    }

    @Test
    public void aForcedChangeGetsAFreshLinkAtSignIn() throws Exception {
        stored.setPasswordReset(true);
        stored.setPasswordResetToken("seeded-by-the-installer");   // no issue time: expired

        Response response = resource.login(credentials("right"), request());

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        assertEquals(1, users.callsTo("setPasswordResetToken").size());
        String token = ((UserView) response.getData()).getPasswordResetToken();
        assertNotEquals("seeded-by-the-installer", token);
        assertTrue(PasswordResetTokens.isFresh(stored, System.currentTimeMillis()));
        assertTrue("signing in changes neither the password nor the sessions",
                users.callsStartingWith("setNewPassword", "updatePassword").isEmpty());
    }

    @Test
    public void aFreshLinkIsKeptAtSignIn() throws Exception {
        stored.setPasswordReset(true);
        PasswordResetTokens.issue(stored, System.currentTimeMillis());
        String token = stored.getPasswordResetToken();

        Response response = resource.login(credentials("right"), request());

        assertEquals(token, ((UserView) response.getData()).getPasswordResetToken());
        assertTrue(users.callsTo("setPasswordResetToken").isEmpty());
    }

    @Test
    public void aFirstSignInStoresOnlyTheAuthToken() throws Exception {
        stored.setAuthToken(null);
        stored.setPassword("hash");

        Response response = resource.login(credentials("right"), request());

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        assertEquals(1, users.callsTo("setAuthToken").size());
        assertTrue(users.callsStartingWith("setNewPassword", "updatePassword", "setPasswordResetToken").isEmpty());
    }

    @Test
    public void aWrongPasswordIssuesNothing() throws Exception {
        stored.setPasswordReset(true);

        Response response = resource.login(credentials("wrong"), request());

        assertEquals(Response.ResponseStatus.ERROR, response.getStatus());
        assertTrue(users.callsTo("setPasswordResetToken").isEmpty());
    }

    private static UserCredentials credentials(String password) {
        UserCredentials c = new UserCredentials();
        c.setLogin("admin");
        c.setPassword(password);
        return c;
    }

    private static HttpServletRequest request() {
        HttpSession session = (HttpSession) Proxy.newProxyInstance(AuthResourceTest.class.getClassLoader(),
                new Class<?>[]{HttpSession.class}, (proxy, method, args) -> null);
        return (HttpServletRequest) Proxy.newProxyInstance(AuthResourceTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> "getSession".equals(method.getName()) ? session : null);
    }
}
