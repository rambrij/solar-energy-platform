package com.saumya.pos.subscription;

import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class MockSubscriptionBillingReminder {
    private final ObjectProvider<JavaMailSender> mail;
    private final boolean local;
    private final GraceReminderTemplate template;

    MockSubscriptionBillingReminder(ObjectProvider<JavaMailSender> mail,Environment environment,GraceReminderTemplate template){
        this.mail=mail;
        this.template=template;
        local=environment.acceptsProfiles(Profiles.of("local & !prod & !production"));
    }

    void afterCommit(String recipient,String invoice,Instant graceUntil){
        if(!local||recipient==null||!recipient.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
            ||!TransactionSynchronizationManager.isSynchronizationActive())return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){
                try{
                    var sender=mail.getIfAvailable();
                    if(sender==null)return;
                    var message=new SimpleMailMessage();
                    message.setFrom("platform-billing@somya.local");
                    message.setTo(recipient);
                    var wording=template.load();
                    if(wording.isEmpty())return;
                    message.setSubject(wording.get().subject());
                    message.setText(wording.get().body()+"\n\nMock invoice "+invoice+" is unpaid. The local test grace period ends "+graceUntil+
                        ". This is a simulation and no funds were collected.");
                    sender.send(message);
                }catch(RuntimeException ex){
                    LoggerFactory.getLogger(MockSubscriptionBillingReminder.class)
                        .warn("Could not send mock subscription grace reminder");
                }
            }
        });
    }
}
