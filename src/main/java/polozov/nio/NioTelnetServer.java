package polozov.nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class NioTelnetServer {
	public static final String LS_COMMAND = "\tls    view all files and directories\n";
	public static final String MKDIR_COMMAND = "\tmkdir    create directory\n";
	public static final String CHANGE_NICKNAME = "\tnick    change nickname\n";
	private static final String CD_COMMAND = "\tcd  change directory\n";
	private static final String TOUCH_COMMAND = "\ttouch  create file\n";
	private static final String RM_COMMAND = "\trm  delete file\n";
	private static final String COPY_COMMAND = "\tcopy  create file\n";
	private static final String CAT_COMMAND = "\tcat  view file content\n";
	private static final String ROOT_NOTIFICATION = "\tYou are already in the root directory\n\r";
	private static final String ROOT_PATH = "server";
	private static final String DIRECTORY_DOES_NOT_EXISTS = "\tFile or directory %s doesn't exists\n";
	private static final String FILE_DIRECTORY_NOTIFICATION = "\tFile or directory %s is already exists\n";

	private final ByteBuffer buffer = ByteBuffer.allocate(512);
	private String nickName;
	private Map<SocketAddress, String> clients = new HashMap<>();
	private Path currentPath = Path.of("server");

	public NioTelnetServer() throws IOException {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress(5678));
		server.configureBlocking(false);
		// OP_ACCEPT, OP_READ, OP_WRITE
		Selector selector = Selector.open();

		server.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server started");

		while (server.isOpen()) {
			selector.select();

			var selectionKeys = selector.selectedKeys();
			var iterator = selectionKeys.iterator();

			while (iterator.hasNext()) {
				var key = iterator.next();
				if (key.isAcceptable()) {
					handleAccept(key, selector);
				} else if (key.isReadable()) {
					handleRead(key, selector);
				}
				iterator.remove();
			}
		}
	}

	private void handleRead(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((SocketChannel) key.channel());
		SocketAddress client = channel.getRemoteAddress();
		int readBytes = channel.read(buffer);
		if (readBytes < 0) {
			channel.close();
			return;
		} else if (readBytes == 0) {
			return;
		}
		buffer.flip();
		StringBuilder sb = new StringBuilder();
		while (buffer.hasRemaining()) {
			sb.append((char) buffer.get());
		}
		buffer.clear();
		// TODO
		// touch [filename] - создание файла
		// mkdir [dirname] - создание директории
		// cd [path] - перемещение по каталогу (.. | ~ )
		// rm [filename | dirname] - удаление файла или папки
		// copy [src] [target] - копирование файла или папки
		// cat [filename] - просмотр содержимого
		// вывод nickname в начале строки

		if (key.isValid()) {
			String command = sb
					.toString()
					.replace("\n", "")
					.replace("\r", "");

			if ("--help".equals(command)) {
				sendMessage(LS_COMMAND, selector, client);
				sendMessage(MKDIR_COMMAND, selector, client);
				sendMessage(CHANGE_NICKNAME, selector, client);
				sendMessage(CD_COMMAND, selector, client);
				sendMessage(RM_COMMAND, selector, client);
				sendMessage(CAT_COMMAND, selector, client);
				sendMessage(COPY_COMMAND, selector, client);
				sendMessage(TOUCH_COMMAND, selector, client);
			}else if ("ls".equals(command)) {
				sendMessage(getFileList().concat("\n"), selector, client);
			}else if (command.startsWith("nick ")) {
				nickName = changeName(command, channel);
			}else if (command.startsWith("cd ")){
				replacePosition(selector, client, command);
			} else if (command.startsWith("mkdir ")) {
				createNewFileDir(command, 'D', selector, client);
			} else if (command.startsWith("touch ")) {
				createNewFileDir(command, 'F', selector, client);
			} else if (command.startsWith("rm ")) {
				deleteFile(command, selector, client);
			} else if (command.startsWith("copy ")) {
				copyFile(command, selector, client);
			} else if (command.startsWith("cat ")) {
				viewFileContent(command, selector, client);
			} else if ("exit".equals(command)) {
				System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
				channel.close();
				return;
			}
		}
		sendName(channel, nickName);
	}

	private String changeName(String command, SocketChannel channel) throws IOException {
		String name = command.split(" ")[1];
		clients.put(channel.getRemoteAddress(), name);
		System.out.println("Client " + channel.getRemoteAddress().toString() + "changed nickname");
		return name;
	}

	private String getFileList() {
		return String.join(" ", new File("server").list());
	}

	private void createNewFileDir(String command, char typeOp, Selector selector, SocketAddress client) throws IOException {
		String neededFileDir = command.split(" ")[1];
		if (!neededFileDir.isEmpty()){
			if (Files.exists(Path.of(currentPath.toString(),neededFileDir))){
				sendMessage(String.format(FILE_DIRECTORY_NOTIFICATION, neededFileDir), selector, client);
			}else {
				if (typeOp == 'D'){
					Files.createDirectory(Path.of(currentPath.toString(), neededFileDir));
				}else if (typeOp == 'F'){
					Files.createFile(Path.of(currentPath.toString(), neededFileDir));
				}
			}
		}
	}

	private void deleteFile(String command, Selector selector, SocketAddress client) throws IOException {
		String FileString = command.split(" ")[1];
		if (!FileString.isEmpty()){
			if (!Files.exists(Path.of(currentPath.toString(),FileString))){
				sendMessage(String.format(DIRECTORY_DOES_NOT_EXISTS, FileString), selector, client);
			}else {
				Files.delete(Path.of(currentPath.toString(), FileString));
			}
		}
	}

	private void copyFile(String command, Selector selector, SocketAddress client) throws IOException {
		String sourceFileString = command.split(" ")[1];
		String targetFileString = command.split(" ")[2];
		if (!Files.exists(Path.of(currentPath.toString(),sourceFileString))){
			sendMessage(String.format(DIRECTORY_DOES_NOT_EXISTS, sourceFileString), selector, client);
		}else
			try{
				Files.copy(Path.of(currentPath.toString(),sourceFileString),
						Path.of(currentPath.toString(),targetFileString), REPLACE_EXISTING);
			}catch (IOException e){
				e.printStackTrace();
				sendMessage("Copying failed: " + e.toString(), selector, client);
			}
	}

	private void viewFileContent(String command, Selector selector, SocketAddress client) throws IOException {
		String viewFileString = command.split(" ")[1];
		if (!Files.exists(Path.of(currentPath.toString(),viewFileString))){
			sendMessage(String.format(DIRECTORY_DOES_NOT_EXISTS, viewFileString), selector, client);
		}else {
			List<String> fileLines = Files.readAllLines(Path.of(currentPath.toString(), viewFileString));
			for (String line: fileLines) {
				sendMessage(line +"\n", selector, client);
			}
		}
	}

	private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
		for (SelectionKey key : selector.keys()) {
			if (key.isValid() && key.channel() instanceof SocketChannel) {
				if (((SocketChannel)key.channel()).getRemoteAddress().equals(client)) {
					((SocketChannel)key.channel())
							.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
				}
			}
		}
	}

	private void sendName(SocketChannel channel, String nickname) throws IOException {
		if (nickname.isEmpty()){
			nickname = clients.getOrDefault(channel.getRemoteAddress(), channel.getRemoteAddress().toString());
		}
		String currentPathString = currentPath.toString().replace("sever","~");
		channel.write(ByteBuffer.wrap(nickname.concat(">:").concat(currentPathString.concat("$"))
				.getBytes(StandardCharsets.UTF_8)));
	}

	private void replacePosition(Selector selector, SocketAddress client, String command) throws IOException {
		String neededPathString = command.split(" ")[1];
		Path tempPath = Path.of(currentPath.toString(), neededPathString);
		if ("..".equals(neededPathString)){
			tempPath = currentPath.getParent(); // server
			if (tempPath == null || !tempPath.toString().startsWith("server")){
				sendMessage( ROOT_NOTIFICATION, selector, client);
			}else currentPath = tempPath;
		}else if ("`".equals(neededPathString)){
			currentPath = Path.of(ROOT_PATH);
		}else {
			if (tempPath.toFile().exists()){
				currentPath = tempPath;
			}else {
				sendMessage(String.format(DIRECTORY_DOES_NOT_EXISTS, neededPathString), selector, client);
			}
		}
	}

	private void handleAccept(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
		channel.configureBlocking(false);
		System.out.println("Client accepted. IP: " + channel.getRemoteAddress());

		channel.register(selector, SelectionKey.OP_READ, "some attach");
		channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
		channel.write(ByteBuffer.wrap("Enter --help for support info\n".getBytes(StandardCharsets.UTF_8)));
		sendName(channel, "");
	}

	public static void main(String[] args) throws IOException {
		new NioTelnetServer();
	}
}
