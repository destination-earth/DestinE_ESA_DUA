require([
	'jquery',
	"splunkjs/mvc/dropdownview",
	"splunkjs/mvc/simplexml/ready!"
], function ($, DropdownView) {
	// HTML interaction
	/* DEMO
	$('#eo-widget-icon').on('click', function () {
		//console.log("eo-widget-icon click");
		$('#eo-widget-icon').css({
			"display": "none"
		});
		$('#eo-chat-section').css({
			"visibility": "visible"
		});
		const eoExpert = document.getElementById("eo-expert2");
		if (eoExpert.children.length == 0) {
			getEOExperts(URL_MODELS, OPTIONS_MODELS);
		}
	});
	$('#eo-header-min-section').on('click', function () {
		//console.log("eo-header-min-section click");
		$('#eo-chat-section').css({
			"width": "",
			"height": "",
			"bottom": "20px",
			"right": "20px",
			"top": "",
			"left": "",
			"max-height": "60vh"
		});
		$('#eo-chat-list').css({
			"width": "",
		});
		$('#eo-chat-window').css({
			"width": "400px",
		});
	});
	$('#eo-header-max-section').on('click', function () {
		//console.log("eo-header-max-section click");
		$('#eo-chat-section').css({
			"width": "100%",
			"height": "100vh",
			"bottom": "0px",
			"right": "0px",
			"top": "0px",
			"left": "0px",
			"max-height": "100dvh"
		});
		$('#eo-chat-list').css({
			"width": "30vh",
		});
		$('#eo-chat-window').css({
			"width": "100%",
		});
	});
	$('#eo-header-close-section').on('click', function () {
		//console.log("eo-header-close-section click");
		$('#eo-widget-icon').css({
			"display": "block"
		});
		$('#eo-chat-section').css({
			"visibility": "hidden"
		});
	});
	*/
	$("#eo-input-box").keyup(function (event) {
		if (event.keyCode === 13) {
			//console.log("eo-input-box enter");
			const prompt = uiUpdateChatMessages();
			askChatBot(prompt);
		}
	});
	$("#eo-send-button").on("click", function () {
		//console.log("eo-send-button click");
		const prompt = uiUpdateChatMessages();
		askChatBot(prompt);
	});

	/* TODO: remove
	$("#eo-expert").on("change", function () {
		knowledge = knowledgeList[this.value];
		model = this.value;
		//console.log("knowledge: ", knowledge);
		console.log("model:", model);
		loadChatHistory();
	});
	*/

	/*--- DEMO
	$("#eo-expert2").on("click", "button", function () {
		knowledge = knowledgeList[this.value];
		model = this.value;
		//console.log("knowledge: ", knowledge);
		console.log("model:", model);
		uiUpdateHeaderSection(model + ".png", expertInfo[model].name, "");
		loadChatHistory();
	});
	*/

	$('#eo-citation-close-section').on('click', function () {
		//console.log("eo-citation-close-section click");
		$('#eo-citation-section').css({
			"visibility": "hidden"
		});
	});
	$('#eo-trash-history').on('click', function () {
		//console.log("eo-trash-history click");
		if (confirm(CONFIRM_DELETE)) {
			cleanChatHistory();
		}
	});

	// JS code
	const MIN_RELEVANCE = 0.5;
	const CONFIRM_DELETE = "Are you sure?";
	const SPLUNK_QUERY_DOCUMENT = "/en-US/app/dua-uad/documents?form.tok_usecase=*&form.tok_mission=*&form.tok_doc_type=*&form.tok_doi=*&form.tok_title=*&form.tok_source=";
	const HMTL_SPINNER = "<i class='fa fa-spinner fa-spin fa-3x fa-fw'></i>";
	const HMTL_IMG_SAT = "<img src='../../static/app/dua-uad/satellite.png' alt='Expert Icon' class='eo-logo'/>";

	var knowledgeList = [];
	var knowledge = null;
	var model = null;
	var chatHistory = [];
	var citationsHistory = [];

	var expertInfo = [];

	var appConfig = {
		URL_CHAT: "",
		URL_MODELS: "",
		API_KEY: "",
		OPTIONS_MODELS: null,
		optionsChat: null,
		splunk_root: ""
	};

	//----- DEMO
	// Splunk DropDown
	let my_dropdown = new DropdownView({
		id: "eo-expert-choice",
		labelField: "mission",
		valueField: "mission",
		selectFirstChoice: true,
		showClearButton: false,
		el: $("#eo-expert-choice")
	}).render();

	my_dropdown.on("change", function () {
		sel = my_dropdown.val();
		//console.log("--> sel: ", sel);
		knowledge = knowledgeList[sel];
		model = sel;
		//console.log("--> knowledge: ", knowledge);
		//console.log("--> model:", model);
		uiUpdateHeaderSection(model + ".png", expertInfo[model].name, expertInfo[model].description);
		loadChatHistory();
	});

	//--------------------
	loadConfiguration('../../static/app/dua-uad/app-config.json');

	function loadConfiguration(filePath) {
		$.get(filePath)
			.done(function (data) {

				/* DEV 
				const URL_CHAT = "http://dua-dev-ml1:3000/ollama/api/chat";
				const URL_MODELS = "http://dua-dev-ml1:3000/api/models";
				const API_KEY = "sk-9f482e5d136743e9b6c3df88ad5e08e0";
				*/

				/* OVH 
				const URL_CHAT = "http://dua-dev-n1:3000/ollama/api/chat";
				const URL_MODELS = "http://dua-dev-n1:3000/api/models";
				const API_KEY = "sk-02f0298483b7468fb3f52ef99e9e66a5";
				*/
				appConfig.URL_CHAT = data.url_chat;
				appConfig.URL_MODELS = data.url_models;
				appConfig.API_KEY = data.api_key;
				appConfig.splunk_root = data.splunk_root;
				//console.log("appConfig:", data);

				appConfig.OPTIONS_MODELS = {
					method: "GET",
					headers: {
						"Accept": "application/json",
						"Authorization": "Bearer " + appConfig.API_KEY
					}
				};

				appConfig.optionsChat = {
					method: "POST",
					headers: {
						"Accept": "application/json",
						"Content-Type": "application/json",
						"Authorization": "Bearer " + appConfig.API_KEY
					},
					body: null,
				};
				// Load EO experts
				getEOExperts(appConfig.URL_MODELS, appConfig.OPTIONS_MODELS);
			})
			.fail(function () {
				console.log("Error loading app-config.json");
			});
	}

	// DOM Chat History render
	function uiRenderChatHistory() {
		// clean-up chat history div
		const chatMsg = document.getElementById("eo-chat-messages");
		chatMsg.innerHTML = '';
		for (i = 0; i < chatHistory.length; i++) {
			const chatItem = uiCreateChatMessage(chatHistory[i].role);
			//console.log("chatItem:", chatItem);
			chatItem.msgArea.innerHTML = chatHistory[i].content;
			// add citations loading from history storage
			if (citationsHistory[i] != null) {
				uiAppendCitations(citationsHistory[i], chatItem.citationContainer);
			}
		}
		uiScrollToBottom();
	}

	// Fill comboBox options with model and knowledge
	// TODO: remove
	function uiCreateOptions(json) {
		const eoExpert = document.getElementById("eo-expert");
		let firstElement = true;
		//console.log("json:", json);
		for (i in json.data) {
			if (json.data[i].info &&
				json.data[i].info.meta.hidden != true &&
				json.data[i].info.meta.knowledge) {
				const optionElement = document.createElement("option");
				id = json.data[i].id;
				optionElement.setAttribute("value", id);
				optionElement.text = json.data[i].name;
				knowledgeList[id] = json.data[i].info.meta.knowledge[0];
				eoExpert.appendChild(optionElement);

				if (firstElement) {
					knowledge = knowledgeList[id];
					model = id;
					firstElement = false;
				}
			}
		}
		//console.log("knowledge:", knowledge);
		console.log("model:", model);
	}

	function uiUpdateHeaderSection(icon, name, description) {
		const eoHeader = document.getElementById("eo-header-logo");
		eoHeader.setAttribute("src", "../../static/app/dua-uad/" + icon);
		const eoHeaderTitle = document.getElementById("eo-header-title");
		eoHeaderTitle.innerHTML = name;
		const eoHeaderDescription = document.getElementById("eo-header-description");
		eoHeaderDescription.innerHTML = description;
	}

	function uiUpdateExpertItems(localModel) {
		if (localModel == null) {
			localModel = model;
		}
		const eoExpertDescription = document.getElementById("eo-chat-description-" + localModel);
		if (expertInfo[localModel] == null) return;
		eoExpertDescription.innerHTML = (expertInfo[localModel].lastMessage).substring(0, 20) + "...";
		const eoExpertDate = document.getElementById("eo-chat-date-" + localModel);

		// Set last message date
		const messageDate = new Date(expertInfo[localModel].lastMessageDate);
		const currentDate = new Date();
		const diffDays = Math.floor(Math.abs(currentDate - messageDate) / (1000 * 60 * 60 * 24));
		if (diffDays < 1) {
			printDate = String(messageDate.getHours()).padStart(2, '0') + ":" + String(messageDate.getMinutes()).padStart(2, '0');
		} else {
			printDate = String(messageDate.getDay()).padStart(2, '0') + "/" + String(messageDate.getMonth() + 1).padStart(2, '0');
		}
		eoExpertDate.innerHTML = printDate;
	}

	function uiCreateExpert(json) {
		const eoExpert = document.getElementById("eo-expert2");
		let expert_choices = [];
		let firstElement = true;
		//console.log("json:", json);
		for (i in json.data) {
			if (json.data[i].info &&
				json.data[i].info.meta.hidden != true &&
				json.data[i].info.meta.knowledge) {

				id = json.data[i].id;
				//name2 = json.data[i].name;
				//name2 = json.data[i].info.name;
				knowledgeList[id] = json.data[i].info.meta.knowledge[0];

				expertInfo[id] = {
					name: knowledgeList[id].name,
					lastMessage: "",
					lastMessageDate: "",
					description: json.data[i].info.meta.description,
					knowledgeDescription: knowledgeList[id].description
				};
				//console.log("expertInfo["+id+"]", expertInfo[id]);
				expert_choices.push({
					label: expertInfo[id].name,
					value: id,
				})

				if (firstElement) {
					knowledge = knowledgeList[id];
					model = id;
					firstElement = false;
				}
				// Create GUI
				uiCreateExpertItems(eoExpert, id, id + ".png", expertInfo[id].name, expertInfo[id].lastMessage, expertInfo[id].lastMessageDate);
				uiUpdateExpertItems(id);
				loadChatHistory(id);
			}
		}
		//console.log("knowledge:", knowledge);
		console.log("model:", model);
		// Update GUI
		my_dropdown.settings.set("choices", expert_choices);
		uiUpdateHeaderSection(model + ".png", expertInfo[model].name, expertInfo[model].description);
		loadChatHistory();
	}

	function uiCreateExpertItems(parent, id, icon, name, description, date) {
		const listItem = document.createElement("li");

		const itemElement = document.createElement("button");
		itemElement.setAttribute("value", id);
		itemElement.setAttribute("class", "eo-chat-item");

		itemElement.innerHTML = "<img src='../../static/app/dua-uad/" + icon + "' alt='Expert Icon' class='eo-logo'/>";

		const itemDetailElement = document.createElement("div");
		itemDetailElement.setAttribute("class", "eo-chat-details");

		const detailNameElement = document.createElement("div");
		detailNameElement.setAttribute("class", "eo-chat-name");
		detailNameElement.innerHTML = name;
		itemDetailElement.appendChild(detailNameElement);

		const itemDescriptionElement = document.createElement("div");
		itemDescriptionElement.setAttribute("id", "eo-chat-description-" + id);
		itemDescriptionElement.setAttribute("class", "eo-chat-description");
		itemDescriptionElement.innerHTML = description;
		itemDetailElement.appendChild(itemDescriptionElement);

		itemElement.appendChild(itemDetailElement);

		const itemDateElement = document.createElement("div");
		itemDateElement.setAttribute("id", "eo-chat-date-" + id);
		itemDateElement.setAttribute("class", "eo-chat-date");
		itemDateElement.innerHTML = date;

		itemElement.appendChild(itemDateElement);

		listItem.appendChild(itemElement);
		parent.appendChild(listItem);
	}

	// Create DOM ChatMessage
	function uiCreateChatMessage(userType) {
		const chatMsg = document.getElementById("eo-chat-messages");

		const msgWrapper = document.createElement("div");
		msgWrapper.setAttribute("class", "eo-message-wrapper eo-" + userType + "-message eo-font");
		chatMsg.appendChild(msgWrapper);

		const msgContainer = document.createElement("div");
		msgWrapper.appendChild(msgContainer);

		const msgItemInner = document.createElement("div");
		msgItemInner.setAttribute("class", "eo-message eo-font eo-" + userType + "-message-bg");
		msgContainer.appendChild(msgItemInner);

		const msgCitations = document.createElement("div");
		msgContainer.appendChild(msgCitations);

		return { citationContainer: msgCitations, msgArea: msgItemInner };
	}

	// Create DOM ChatButton
	function uiCreateChatButton(index) {
		const msgCitationButton = document.createElement("button");
		msgCitationButton.setAttribute("style", "margin:1px; padding:2px");
		const msgCitationIndex = document.createElement("div");
		msgCitationIndex.setAttribute("class", "bg-white dark:bg-gray-700 rounded-full size-4");
		msgCitationIndex.innerHTML = index;
		msgCitationButton.appendChild(msgCitationIndex);
		return msgCitationButton;
	}

	// Scroll the Chat Window to last message
	function uiScrollToBottom() {
		const messages = document.getElementById("eo-chat-messages");
		messages.scroll(0, messages.scrollHeight);
	}

	// Append message to Chat
	function uiAppendAssistantMessage(json, assistantResponse) {
		if (assistantResponse.innerText == "") {
			assistantResponse.innerHTML = '';
		}
		if (json.message && json.message.content) {
			assistantResponse.innerText += json.message.content;
			//console.log(json.message.content);
		}
		uiScrollToBottom();
		return assistantResponse.innerText;
	}

	// DOM create Citation Window
	function uiCreateCitationWindow(relevance, name, content) {
		const citationWindow = document.getElementById("eo-citation-section");
		//citationWindow.setAttribute("style", "visibility: visible;");
		citationWindow.style.visibility = "visible";
		const citationSource = document.getElementById("eo-citation-name");

		citationSource.innerHTML = "";
		const anchor = document.createElement("a");

		let nameLink = name.replace(".json", "");
		let nameLink1 = nameLink.replace("_", "%2F");
		nameLink1 = nameLink1.replaceAll("-", "%2D");

		anchor.setAttribute("href", appConfig.splunk_root + SPLUNK_QUERY_DOCUMENT + nameLink1);
		anchor.setAttribute("target", "_blank");
		anchor.innerText = nameLink;
		citationSource.appendChild(anchor);

		const citationRelevance = document.getElementById("eo-citation-relevance");
		citationRelevance.innerHTML = relevance;
		const citationContent = document.getElementById("eo-citation-content");
		citationContent.innerHTML = content;
	}

	// Append citations to Chat
	function uiAppendCitations(json, citationMsg) {
		//console.log("json.citations:", json);
		const length = json[0].metadata.length;
		let i = 1;
		for (let j = 0; j < length; j++) {
			const relevance = `${(100 * (1 - json[0].distances[j])).toFixed(2)} %`;
			const name = json[0].metadata[j].name;
			const content = json[0].document[j];
			const button = uiCreateChatButton(i++);
			button.addEventListener("click", function (event) {
				uiCreateCitationWindow(relevance, name, content);
			}, false);
			citationMsg.appendChild(button);
		}
	}

	// Clean Chat History
	function cleanChatHistory() {
		localStorage.setItem("chatHistory-" + model, null);
		localStorage.setItem("citationsHistory-" + model, null);
		localStorage.setItem("chatLastMessageDate-" + model, null);
		loadChatHistory();
	}

	// Save Chat History
	function saveChatHistory() {
		localStorage.setItem("chatHistory-" + model, JSON.stringify(chatHistory));
		localStorage.setItem("citationsHistory-" + model, JSON.stringify(citationsHistory));
		localStorage.setItem("chatLastMessageDate-" + model, JSON.stringify(expertInfo[model].lastMessageDate));
		return { chat: chatHistory, citations: citationsHistory };
	}

	// Load Chat History
	function loadChatHistory(localModel) {
		if (localModel == null) {
			localModel = model;
		}
		// load from browser storage
		chatHistory = JSON.parse(localStorage.getItem("chatHistory-" + localModel));
		citationsHistory = JSON.parse(localStorage.getItem("citationsHistory-" + localModel));
		expertInfo[localModel].lastMessageDate = JSON.parse(localStorage.getItem("chatLastMessageDate-" + localModel));

		//console.log("localModel:", localModel);

		if (chatHistory == null) {
			//TODO: move to icon and presentation html area
			const greetingMessage = "Hi, ask me something about the <b>" + expertInfo[localModel].knowledgeDescription + "</b>!<br/>";

			chatHistory = [];
			citationsHistory = [];
			//console.log("initialize " + localModel +" chat history...");
			const savedData = appendHistory("assistant", greetingMessage, null);
			//console.log("savedData:", savedData);
			chatHistory = savedData.chat;
		}
		// Last message is always from assistant
		expertInfo[localModel].lastMessage = chatHistory.slice(-1)[0].content;
		//console.log("expertInfo[localModel].lastMessage:", expertInfo[localModel].lastMessage);
		//console.log("chatHistory:", chatHistory);
		uiRenderChatHistory();
		uiUpdateExpertItems();
	}

	// Append message to in-memory chat history
	function appendHistory(role, content, citations) {
		const historyItem = {
			"role": role,
			"content": content
		};
		const count = chatHistory.push(historyItem);

		if (citations != null) {
			//console.log("citations:", citations);
			citationsHistory[count - 1] = citations;
		};

		if (role == "assistant") {
			var currentdate = new Date();
			expertInfo[model].lastMessageDate = currentdate;
		}

		return saveChatHistory();
	}

	// Remove irrelevant Citations
	function removeIrrelevantCitations(json) {
		var relevantCitations = [{
			metadata: [],
			distances: [],
			document: []
		}];
		const length = json[0].metadata.length;
		for (let j = 0; j < length; j++) {
			// include only relevant citations
			if (json && json[0].metadata && (json[0].distances[j] <= (1 - MIN_RELEVANCE))) {
				relevantCitations[0].metadata.push(json[0].metadata[j]);
				relevantCitations[0].distances.push(json[0].distances[j]);
				relevantCitations[0].document.push(json[0].document[j]);
			}
		}
		//console.log("json:", json);
		//console.log("relevantCitations:", relevantCitations);
		return relevantCitations;
	}

	// Create Payload for OI model
	function createPayload(userInput) {
		appendHistory("user", userInput, null);
		const payload = {
			"stream": true,
			"model": model,
			"messages": chatHistory,
			"options": {
				"temperature": 0.1
			},
			"files": [
				knowledge
			]
		};
		return JSON.stringify(payload);
	}

	// Get answer from ChatBot
	function uiUpdateChatMessages() {
		const inputBox = document.getElementById("eo-input-box")
		let assistantMsg = null;
		let prompt = null;
		const userInput = inputBox.value;
		console.log("userInput:", userInput);
		if (userInput.trim() != "") {
			const userMsgElements = uiCreateChatMessage("user");
			userMsgElements.msgArea.innerText = userInput;
			const assistantMsgElements = uiCreateChatMessage("assistant");
			assistantMsgElements.msgArea.innerHTML = HMTL_SPINNER;
			uiScrollToBottom();
			inputBox.value = "";
			prompt = { userInput: userInput, assistantMsgElements: assistantMsgElements };
		}
		return prompt;
	}

	// Send prompt to Assistant
	function askChatBot(prompt) {
		if (prompt != null) {
			const assistantMsgElements = prompt.assistantMsgElements;
			// update optionsChat with user input
			appConfig.optionsChat.body = createPayload(prompt.userInput);
			fetchJsonStream(appConfig.URL_CHAT, appConfig.optionsChat, assistantMsgElements.msgArea, assistantMsgElements.citationContainer);
		}
	}

	// Get AI expert model 
	async function getEOExperts(url, options) {
		console.log("getEOExperts call");

		const response = await fetch(url, options);
		if (!response.ok) {
			throw new Error('Network response was not ok');
		}
		const data = await response.json();
		//console.log(data); // json data

		// Fill comboBox
		//uiCreateOptions(data); //TODO: remove
		uiCreateExpert(data);

		// Populate chat history
		loadChatHistory();
	}

	// Fetch stream from AI expert model and update Message 
	async function fetchJsonStream(url, options, assistantMsg, citationMsg) {
		console.log("fetchJsonStream call");

		const response = await fetch(url, options);

		if (!response.ok) {
			throw new Error('Network response was not ok');
		}

		const reader = response.body.getReader();
		const decoder = new TextDecoder('utf-8');
		let result = '';
		let assistantResponse = '';
		let citations = null;

		while (true) {
			const { done, value } = await reader.read();

			if (done) {
				break; // Stream is finished
			}

			// Decode the chunk and append it to the result
			result += decoder.decode(value, { stream: true });

			// Split the result into individual JSON objects if they are separated by newlines
			const chunks = result.split('\n');

			// Process all but the last chunk (which may be incomplete)
			for (let i = 0; i < chunks.length - 1; i++) {
				try {
					const json = JSON.parse(chunks[i]);
					//console.log(json); // Log the JSON object
					if (json.citations) {
						citations = removeIrrelevantCitations(json.citations);
						uiAppendCitations(citations, citationMsg);
					} else {
						assistantResponse = uiAppendAssistantMessage(json, assistantMsg);
					}
				} catch (e) {
					console.error('Error parsing JSON:', e);
				}
			}
			// Keep the last chunk in case it's incomplete
			result = chunks[chunks.length - 1];
		}

		// Handle any remaining data after the stream ends
		if (result) {
			try {
				const json = JSON.parse(result);
				//console.log(json); // Log the last JSON object
				assistantResponse = uiAppendAssistantMessage(json, assistantMsg);
			} catch (e) {
				console.error('Error parsing JSON:', e);
			}
		}
		appendHistory("assistant", assistantResponse, citations);
		uiUpdateExpertItems();
		console.log("Json strem end.");
	}

});
