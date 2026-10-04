package org.example;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Логика диалога чат-бота.
 * Отвечает на команду /start (приветствие и описание возможностей),
 * команду /help (повторное описание) и возвращает текст пользователя.
 */
public class BotLogic {

    /** Текст приветствия при старте диалога и по команде /help. */
    private static final String HELP_TEXT =
            "Привет! Я - Repetition-Bot.\n"
                    + "Я умею:\n"
                    + "- возвращать любой текст, который ты пришлёшь;\n"
                    + "- по команде /help снова рассказывать, как со мной взаимодействовать.\n";

    /**
     * Создаёт ответ бота на сообщение пользователя.
     *
     * @param chatId идентификатор чата, в который будет отправлен ответ
     * @param text   текст сообщения пользователя
     * @return готовый ответ бота в виде объекта SendMessage
     */
    public SendMessage handleMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        String trimmedText = text.trim();

        if ("/start".equals(trimmedText) || "/help".equals(trimmedText)) {
            message.setText(HELP_TEXT);
        } else {
            message.setText("Вы написали \"" + trimmedText + "\"");
        }

        return message;
    }
}
