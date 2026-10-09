package com.blockworlds.utags.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A form in plain data: a title, a text and a list of buttons (a two-button one is shown as a modal yes/no form).
 * Keeping it free of Cumulus lets the form builders be tested without Floodgate; {@link FloodgateSender} turns it into
 * a real form. {@code onSelect} gets the index of the clicked button.
 */
public record BedrockMenu(String title, String content, List<String> buttons, IntConsumer onSelect, boolean modal) {

    /** Collects buttons together with what each one does. */
    public static final class Builder {
        private final String title;
        private String content = "";
        private final List<String> texts = new ArrayList<>();
        private final List<Runnable> actions = new ArrayList<>();

        public Builder(String title) {
            this.title = title;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder button(String text, Runnable action) {
            texts.add(text);
            actions.add(action);
            return this;
        }

        public BedrockMenu build() {
            return new BedrockMenu(title, content, List.copyOf(texts), i -> {
                if (i >= 0 && i < actions.size()) actions.get(i).run();
            }, false);
        }

        /** A yes/no form: {@code yes} is the first button, {@code no} the second. */
        public BedrockMenu buildModal() {
            if (texts.size() != 2) throw new IllegalStateException("A modal form has exactly two buttons");
            return new BedrockMenu(title, content, List.copyOf(texts), i -> {
                if (i >= 0 && i < actions.size()) actions.get(i).run();
            }, true);
        }
    }

    /** Runs the action of button {@code index}, as a click would. */
    public void select(int index) {
        onSelect.accept(index);
    }
}
