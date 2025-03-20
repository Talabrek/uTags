private final ErrorHandler errorHandler;
    
    /**
     * Creates a new AdminCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public AdminCommandHandler(uTags plugin) {
        super(plugin);
        this.errorHandler = new ErrorHandler(plugin);
    }
    
    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        try {
            // Check if sender is a player
            Player player = errorHandler.checkPlayer(sender);
            if (player == null) {
                return false;
            }
            
            // Check admin permission
            if (!errorHandler.checkPermission(player, PERM_ADMIN)) {
                return false;
            }
            
            // Display usage if no subcommand
            if (args.length < 1) {
                displayAdminUsage(player);
                return true;
            }
            
            // Handle appropriate subcommand
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            
            // Use ExceptionRunner for standardized error handling in each subcommand
            ErrorHandler.ExceptionRunner runner = errorHandler.createExceptionRunner(
                sender, "executing admin command '" + subCommand + "'"
            );
            
            boolean result = false;
            switch (subCommand) {
                case "create":
                    result = runner.run(() -> handleCreateCommand(player, subArgs));
                    break;
                case "delete":
                    result = runner.run(() -> handleDeleteCommand(player, subArgs));
                    break;
                case "edit":
                    result = runner.run(() -> handleEditCommand(player, subArgs));
                    break;
                case "purge":
                    result = runner.run(() -> handlePurgeCommand(player, subArgs));
                    break;
                case "requests":
                    result = runner.run(() -> handleRequestsCommand(player));
                    break;
                default:
                    displayAdminUsage(player);
                    result = true;
                    break;
            }
            
            return result;
        } catch (Exception e) {
            // Catch any unexpected exceptions
            return errorHandler.handleException(e, sender, "processing admin command");
        }
    }
