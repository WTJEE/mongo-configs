package xyz.wtje.example.help.config;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.List;

@ConfigsFileProperties(name = "help-example")
@SupportedLanguages({"en", "pl"})
public class HelpMessages {

    public Gui gui = new Gui();
    public Commands commands = new Commands();
    public Chat chat = new Chat();

    public static class Gui {
        public String title = "&8Help Center";
        public Items items = new Items();

        public static class Items {
            public HelpItem help = new HelpItem();
            public LanguageItem language = new LanguageItem();

            public static class HelpItem {
                public String name = "&aCommands";
                public List<String> lore = List.of(
                    "&7Click to print the command list",
                    "&7You are viewing translations for &f{language_display}&7."
                );
            }

            public static class LanguageItem {
                public String name = "&b{language_display}";
                public List<String> lore = List.of(
                    "&7Switch to this language",
                    "&7Code: &f{language_code}",
                    "&7Selected: &f{selected}"
                );
            }
        }
    }

    public static class Commands {
        public List<String> entries = List.of(
            "&e/help &7- Show available commands",
            "&e/mongoconfigs reloadall &7- Reload MongoDB collections",
            "&e/language &7- Pick your language",
            "&e/helpui &7- Open this menu"
        );
    }

    public static class Chat {
        public String loading = "&eOpening the help menu...";
        public String languageChanged = "&aLanguage changed to &f{language_display}&a.";
        public String commandsHeader = "&7--- &aHelp Commands &7---";
    }
}
