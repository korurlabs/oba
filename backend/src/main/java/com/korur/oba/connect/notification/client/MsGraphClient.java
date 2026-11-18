package com.korur.oba.connect.notification.client;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

// 2. Third-party
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.User;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.microsoft.kiota.ApiException;

// 3. Java SDK
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// 4. Internal
import static com.korur.oba.connect.notification.constant.client.MsGraphClientLogConstant.*;
import com.korur.oba.connect.notification.dto.AttachmentEmail;
import com.korur.oba.connect.notification.dto.GraphMailbox;
import com.korur.oba.connect.notification.dto.GraphMailboxLookup;
import com.korur.oba.connect.notification.dto.Mail;
import com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Client for the Microsoft Graph mail API of the configured Microsoft 365 tenant: it
 * sends mail and looks up mailboxes, with the application's own credentials (client
 * credentials flow), not on behalf of a signed-in user.
 *
 * Used for platform notification mails by
 * {@link com.korur.oba.connect.notification.service.impl.EmailNotificationServiceImpl}, and by
 * apps that send mail of their own (the outreaches of Otak) or check a mailbox before they
 * send from it (the mail senders of Otak). Connect code never names those app classes,
 * so an app's callers are described here, not linked.
 */
@Component("emailMsGraphClient")
public class MsGraphClient {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MsGraphClient.class);

    private final GraphServiceClient graphServiceClient;
    private final LoggingService loggingService;

    public MsGraphClient(
            @Qualifier("emailGraphServiceClient") GraphServiceClient graphServiceClient,
            LoggingService loggingService) {
        this.graphServiceClient = graphServiceClient;
        this.loggingService = loggingService;
    }

    /**
     * Sends one mail through Microsoft Graph from the mailbox named by
     * {@link Mail#sender()}: its subject, its content as an HTML body, its recipients as To
     * recipients and its attachments as file attachments, all in one sendMail request.
     *
     * The sender is a user id in Graph terms: a user principal name or mail address for a
     * personal mailbox, or the Graph object id of a shared mailbox, because a shared
     * mailbox's address may differ from its user principal name (see
     * {@link GraphMailbox#id()}). The application must hold the Mail.Send permission for
     * that mailbox. Graph stores the sent mail in the sender's Sent Items.
     *
     * A failed call is retried up to three attempts in total, two and then four seconds
     * apart, by {@link Retryable}; the last failure reaches the caller unchanged. A mail
     * without recipients is skipped with a warning and no call.
     *
     * Called by
     * {@link com.korur.oba.connect.notification.service.impl.EmailNotificationServiceImpl#sendEmail} for
     * notification mails, and by the outreach send of the Otak app, after it claimed the
     * outreach as SENT; that caller marks the outreach FAILED when this method throws, so
     * the outreach can be sent again.
     *
     * @param emailMessage the sender, the recipients, the subject, the HTML content and the attachments
     * @throws RuntimeException whatever the Graph SDK throws once the retries are spent, such
     *         as {@link ApiException} when Graph refuses the request; the callers translate it
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendEmail(Mail emailMessage) {
        if (emailMessage.recipients().isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.CLIENT,
                    "Skipping email send: no recipients provided. subject={}", emailMessage.subject());
            return;
        }

        Message message = new Message();
        message.setSubject(emailMessage.subject());

        ItemBody body = new ItemBody();
        body.setContent(emailMessage.content());
        body.setContentType(BodyType.Html);
        message.setBody(body);

        LinkedList<Recipient> recipients = emailMessage.recipients().stream().map(recipientEmail -> {
            Recipient toRecipients = new Recipient();
            EmailAddress emailAddress = new EmailAddress();
            emailAddress.setAddress(recipientEmail);
            toRecipients.setEmailAddress(emailAddress);
            return toRecipients;
        }).collect(Collectors.toCollection(LinkedList::new));
        message.setToRecipients(recipients);

        // Each attachment is sent inline as a fileAttachment, its bytes base64-encoded in the
        // request by the SDK; that is why callers cap the total size of the attachments.
        if (emailMessage.attachments() != null && !emailMessage.attachments().isEmpty()) {
            List<Attachment> msGraphAttachments = new ArrayList<>();
            for (AttachmentEmail att : emailMessage.attachments()) {
                FileAttachment fileAttachment = new FileAttachment();
                fileAttachment.setOdataType("#microsoft.graph.fileAttachment");
                fileAttachment.setName(att.filename());
                fileAttachment.setContentType(att.contentType());
                fileAttachment.setContentBytes(att.content());
                msGraphAttachments.add(fileAttachment);
            }
            message.setAttachments(msGraphAttachments);
        }

        // POST /users/{sender}/sendMail sends from that mailbox, never from a signed-in user.
        final SendMailPostRequestBody postRequest = new SendMailPostRequestBody();
        postRequest.setMessage(message);
        graphServiceClient.users().byUserId(emailMessage.sender()).sendMail().post(postRequest);
    }

    /**
     * Looks up a mailbox of the tenant by its mail address, so an address can be checked
     * before the application sends from it. Only the mail address is read. An account that
     * Graph returns without a mail address has no mailbox, which is told apart from an
     * address that names no account. The object id is whatever Graph returns without
     * selecting it, so callers of this lookup do not rely on it.
     *
     * Called by the mail sender validation of the Otak app when a personal mail sender is
     * added.
     *
     * @param mailAddress the mail address or user principal name to look up
     * @return {@link GraphMailboxLookup.Found} with the mailbox, {@link GraphMailboxLookup.AccountNotFound}
     *         when Graph answers not found, or {@link GraphMailboxLookup.AccountWithoutMailbox} when the
     *         account has no mail address
     * @throws BadGatewayException {@link ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION}
     *         when Microsoft Graph fails or refuses the call for another reason than an unknown user
     */
    public GraphMailboxLookup findMailbox(String mailAddress) {
        User graphUser;
        try {
            graphUser = graphServiceClient.users().byUserId(mailAddress).get(configuration ->
                    configuration.queryParameters.select = new String[]{SELECT_MAIL});
        } catch (ApiException exception) {
            if (exception.getResponseStatusCode() == HttpStatus.NOT_FOUND.value()) {
                loggingService.info(LOG_PREFIX, LogLayer.CLIENT, MAILBOX_ACCOUNT_NOT_FOUND);
                return new GraphMailboxLookup.AccountNotFound();
            }
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, MAILBOX_LOOKUP_FAILED, exception,
                    exception.getResponseStatusCode());
            throw new BadGatewayException(ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION);
        } catch (RuntimeException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, MAILBOX_LOOKUP_FAILED_WITHOUT_ANSWER, exception);
            throw new BadGatewayException(ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION);
        }

        if (graphUser == null || graphUser.getMail() == null) {
            loggingService.info(LOG_PREFIX, LogLayer.CLIENT, MAILBOX_ACCOUNT_WITHOUT_MAILBOX);
            return new GraphMailboxLookup.AccountWithoutMailbox();
        }
        loggingService.info(LOG_PREFIX, LogLayer.CLIENT, MAILBOX_FOUND);
        return new GraphMailboxLookup.Found(new GraphMailbox(graphUser.getId(), graphUser.getMail()));
    }

    /**
     * Looks up a shared mailbox of the tenant by its primary mail address. A shared mailbox can
     * have a mail address that differs from its user principal name, and Graph resolves
     * /users/{id} only by object id or user principal name, so the address is matched with a
     * filter on the mail property instead. Only member accounts match, because a guest has no
     * mailbox in the tenant. The object id and the mail address are read; the object id is
     * what a send from the shared mailbox addresses.
     *
     * Called by the mail sender validation of the Otak app, both when a shared mail sender
     * is added and at every outreach send from one, which passes the object id to
     * {@link #sendEmail} as the sender.
     *
     * @param mailAddress the primary mail address to look up
     * @return the mailbox with its object id, or empty when no member account has this mail address
     * @throws BadGatewayException {@link ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION}
     *         when Microsoft Graph fails or refuses the call
     */
    public Optional<GraphMailbox> findSharedMailbox(String mailAddress) {
        // A single quote in the address is escaped for OData, so it cannot end the filter's
        // string literal early and change the filter.
        String filter = String.format(FILTER_MEMBER_MAIL_EQUALS,
                mailAddress.replace(ODATA_SINGLE_QUOTE, ODATA_ESCAPED_SINGLE_QUOTE));
        UserCollectionResponse response;
        try {
            response = graphServiceClient.users().get(configuration -> {
                configuration.queryParameters.filter = filter;
                configuration.queryParameters.select = new String[]{SELECT_ID, SELECT_MAIL};
                configuration.queryParameters.top = 1;
            });
        } catch (ApiException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, MAILBOX_LOOKUP_FAILED, exception,
                    exception.getResponseStatusCode());
            throw new BadGatewayException(ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION);
        } catch (RuntimeException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, MAILBOX_LOOKUP_FAILED_WITHOUT_ANSWER, exception);
            throw new BadGatewayException(ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION);
        }

        Optional<User> member = Optional.ofNullable(response)
                .map(UserCollectionResponse::getValue)
                .flatMap((List<User> users) -> users.stream().findFirst());
        if (member.isEmpty() || member.get().getMail() == null) {
            loggingService.info(LOG_PREFIX, LogLayer.CLIENT, SHARED_MAILBOX_NOT_FOUND);
            return Optional.empty();
        }
        loggingService.info(LOG_PREFIX, LogLayer.CLIENT, SHARED_MAILBOX_FOUND);
        return Optional.of(new GraphMailbox(member.get().getId(), member.get().getMail()));
    }
}
