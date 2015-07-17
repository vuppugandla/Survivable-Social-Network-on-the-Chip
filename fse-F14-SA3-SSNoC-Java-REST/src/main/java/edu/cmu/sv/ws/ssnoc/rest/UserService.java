package edu.cmu.sv.ws.ssnoc.rest;

import javax.crypto.SecretKey;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.h2.util.StringUtils;
import com.google.gson.Gson;


import edu.cmu.sv.ws.ssnoc.common.exceptions.ServiceException;
import edu.cmu.sv.ws.ssnoc.common.exceptions.UnauthorizedUserException;
import edu.cmu.sv.ws.ssnoc.common.exceptions.ValidationException;
import edu.cmu.sv.ws.ssnoc.common.logging.Log;
import edu.cmu.sv.ws.ssnoc.common.utils.ConverterUtils;
import edu.cmu.sv.ws.ssnoc.common.utils.SSNCipher;
import edu.cmu.sv.ws.ssnoc.data.dao.DAOFactory;
import edu.cmu.sv.ws.ssnoc.data.dao.IUserDAO;
import edu.cmu.sv.ws.ssnoc.data.po.UserPO;
import edu.cmu.sv.ws.ssnoc.dto.User;

/**
 * This class contains the implementation of the RESTful API calls made with
 * respect to users.
 * 
 */

@Path("/user")
public class UserService extends BaseService {
	/**
	 * This method checks the validity of the user name and if it is valid, adds
	 * it to the database
	 * 
	 * @param user
	 *            - An object of type User
	 * @return - An object of type Response with the status of the request
	 */
	@POST
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("/signup")
	public Response addUser(User user) {
		Log.enter(user);
		User resp = new User();

		try {
			IUserDAO dao = DAOFactory.getInstance().getUserDAO();
			UserPO existingUser = dao.findByName(user.getUserName());

			// Validation to check that user name should be unique
			// in the system. If a new users tries to register with
			// an existing userName, notify that to the user.
			if (existingUser != null) {
				Log.trace("User name provided already exists. Validating if it is same password ...");
				if (!validateUserPassword(user.getPassword(), existingUser)) {
					Log.warn("Password is different for the existing user name.");
					throw new ValidationException("User name already taken");
				} else {
					Log.debug("Yay!! Password is same for the existing user name.");

					resp.setUserName(existingUser.getUserName());
					return ok(resp);
				}
			}

			UserPO po = ConverterUtils.convert(user);
			po = SSNCipher.encryptPassword(po);
            po.setAccountStatus("1"); //default value, Tangent edited, 10/30/2014
            po.setPrivilegeLevel("Citizen"); //default value, Tangent edited, 10/30/2014

			dao.save(po);
			resp = ConverterUtils.convert(po);
		} catch (Exception e) {
			handleException(e);
		} finally {
			Log.exit();
		}

		return created(resp);
	}

	/**
	 * This method is used to login a user.
	 * 
	 * @param user
	 *            - User information to login
	 * 
	 * @return - Status 200 when successful login. Else other status.
	 */
	@POST
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("/{userName}/authenticate")
	public Response loginUser(@PathParam("userName") String userName,
			User user) {
		Log.enter(userName, user);

		try {
			UserPO po = loadExistingUser(userName);
			if (!validateUserPassword(user.getPassword(), po)) {
				throw new UnauthorizedUserException(userName);
			}
		} catch (Exception e) {
			handleException(e);
		} finally {
			Log.exit();
		}

		return ok();
	}

	/**
	 * This method will validate the user's password based on what information
	 * is sent from the UI, versus the information retrieved for that user from
	 * the database.
	 * 
	 * @param password
	 *            - Encrypted Password
	 * @param po
	 *            - User info from DB
	 * 
	 * @return - Flag specifying YES or NO
	 */
	private boolean validateUserPassword(String password, UserPO po) {
		try {
			SecretKey key = SSNCipher.getKey(StringUtils.convertHexToBytes(po
					.getSalt()));
			if (password.equals(SSNCipher.decrypt(
					StringUtils.convertHexToBytes(po.getPassword()), key))) {
				return true;
			}
		} catch (Exception e) {
			Log.error("An Error occured when trying to decrypt the password", e);
			throw new ServiceException("Error when trying to decrypt password",
					e);
		}

		return false;
	}

	/**
	 * All all information related to a particular userName.
	 * 
	 * @param userName
	 *            - User Name
	 * 
	 * @return - Details of the User
	 */
	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("/{userName}")
	public User loadUser(@PathParam("userName") String userName) {
		Log.enter(userName);

		User user = null;
		try {
			UserPO po = loadExistingUser(userName);
			user = ConverterUtils.convert(po);
		} catch (Exception e) {
			handleException(e);
		} finally {
			Log.exit(user);
		}

		return user;
	}

    /**
     * Update a certain user profile. Created by Tangent on 10/24/14.
     *
     * @param updatedUser
     *            - User
     * @return - Details of the User
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{userName}")
    public Response administerUserProfile(@PathParam("userName") String userName, User updatedUser) {

        Log.enter(updatedUser);
        User resp = new User();

        try {
            //getting current details of user
            UserPO po = loadExistingUser(userName);

            if (po == null) {
                Log.warn("No existing user.");
                return null;
            }

            //checking if all user details are updated or else fill them with existing user details
            if(updatedUser.getUserName()==""){
                updatedUser.setUserName(po.getUserName());
            }
            if(updatedUser.getPassword()==""){
                SecretKey key = SSNCipher.getKey(StringUtils.convertHexToBytes(po
                        .getSalt()));
                updatedUser.setPassword(SSNCipher.decrypt(
                        StringUtils.convertHexToBytes(po.getPassword()), key));
            }
            if(updatedUser.getAccountStatus()==""){
                updatedUser.setAccountStatus(po.getAccountStatus());
            }
            if(updatedUser.getPrivilegeLevel()==""){
                updatedUser.setPrivilegeLevel(po.getPrivilegeLevel());
            }
            //converting the dto to po
            UserPO newUserDetails = ConverterUtils.convert(updatedUser);

            //encrypting the password
            newUserDetails = SSNCipher.encryptPassword(newUserDetails);

            //updating the details
            IUserDAO dao = DAOFactory.getInstance().getUserDAO();
            dao.updateUserProfile(po,newUserDetails);

        } catch (Exception e) {
            handleException(e);
        } finally {
            Log.exit();
        }

        if(updatedUser.getUserName().equals(userName)){
            return ok(updatedUser);
        }
        else{
            return created(updatedUser);
        }

    }




}